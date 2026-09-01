package com.brad.pms.auth;

import com.brad.pms.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import java.util.Hashtable;

@Component
public class DefaultLdapDirectoryClient implements LdapDirectoryClient {

    @Override
    public String authenticate(AuthProviderProperties.Ldap ldap, String identifier, String password) {
        if (ldap.getUrl() == null || ldap.getUrl().isBlank()) {
            throw BusinessException.error("目录登录未配置服务器");
        }
        if (identifier == null || identifier.isBlank() || password == null || password.isBlank()) {
            throw BusinessException.unauthorized("邮箱或密码错误");
        }
        String userDn;
        String email;
        if (hasText(ldap.getManagerDn())) {
            DirContext manager = bind(ldap.getUrl(), ldap.getManagerDn(), ldap.getManagerPassword());
            try {
                SearchResult found = searchUser(manager, ldap, identifier);
                userDn = found.getNameInNamespace();
                email = attribute(found, ldap.getEmailAttribute());
            } finally {
                close(manager);
            }
        } else if (hasText(ldap.getUserDnPattern())) {
            userDn = ldap.getUserDnPattern().replace("{0}", identifier);
            email = identifier.contains("@") ? identifier : null;
        } else {
            throw BusinessException.error("目录登录未配置查询方式");
        }
        DirContext userContext = bind(ldap.getUrl(), userDn, password);
        try {
            if (email == null || email.isBlank()) {
                SearchResult found = searchUser(userContext, ldap, identifier);
                email = attribute(found, ldap.getEmailAttribute());
            }
        } finally {
            close(userContext);
        }
        if (email == null || email.isBlank()) {
            throw BusinessException.unauthorized("目录未返回邮箱，无法登录");
        }
        return email;
    }

    private SearchResult searchUser(DirContext context, AuthProviderProperties.Ldap ldap, String identifier) {
        try {
            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            controls.setCountLimit(1);
            controls.setReturningAttributes(new String[]{ldap.getEmailAttribute()});
            String base = hasText(ldap.getBaseDn()) ? ldap.getBaseDn() : "";
            NamingEnumeration<SearchResult> results = context.search(base, LdapFilters.apply(ldap.getUserSearchFilter(), identifier), controls);
            if (!results.hasMore()) {
                throw BusinessException.unauthorized("邮箱或密码错误");
            }
            return results.next();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw BusinessException.unauthorized("无法连接目录服务");
        }
    }

    private DirContext bind(String url, String dn, String password) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, url);
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, dn);
        env.put(Context.SECURITY_CREDENTIALS, password == null ? "" : password);
        try {
            return new InitialDirContext(env);
        } catch (Exception e) {
            throw BusinessException.unauthorized("邮箱或密码错误");
        }
    }

    private static String attribute(SearchResult result, String name) {
        try {
            Attribute attribute = result.getAttributes().get(name);
            return attribute == null ? null : String.valueOf(attribute.get());
        } catch (Exception e) {
            return null;
        }
    }

    private static void close(DirContext context) {
        try {
            context.close();
        } catch (Exception ignored) {
            // Directory context close is best-effort.
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
