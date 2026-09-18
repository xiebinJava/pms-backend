package com.brad.pms.ai.context;

/** Builds one authoritative snapshot for a supported page type. */
public interface PageContextAssembler {

    PageContextType supports();

    PageContextSnapshot assemble(PageContextRequest request);
}
