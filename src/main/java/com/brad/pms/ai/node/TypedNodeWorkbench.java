package com.brad.pms.ai.node;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Adapter for the typed node workbenches. It derives the writable field set from
 * the workbench's own update command, so a field added to a workbench becomes
 * writable through the Agent without new Agent code.
 */
public final class TypedNodeWorkbench implements NodeWorkbench {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final String key;
    private final String label;
    private final Class<?> commandType;
    private final String documentPath;
    private final BiFunction<Long, Long, Object> reader;
    private final Saver saver;
    private final ObjectMapper mapper;
    private final NodeFieldLabels labels;

    public TypedNodeWorkbench(String key,
                              Class<?> commandType,
                              BiFunction<Long, Long, Object> reader,
                              Saver saver,
                              ObjectMapper mapper,
                              NodeFieldLabels labels) {
        this(key, commandType, null, reader, saver, mapper, labels);
    }

    /**
     * @param documentPath optional nested document the workbench reads and writes
     *                     (for example {@code solutionPackage} inside the solution
     *                     design response); {@code null} means the response root.
     */
    public TypedNodeWorkbench(String key,
                              Class<?> commandType,
                              String documentPath,
                              BiFunction<Long, Long, Object> reader,
                              Saver saver,
                              ObjectMapper mapper,
                              NodeFieldLabels labels) {
        this.key = key;
        this.label = labels.workbench(key);
        this.commandType = commandType;
        this.documentPath = documentPath;
        this.reader = reader;
        this.saver = saver;
        this.mapper = mapper;
        this.labels = labels;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public String label() {
        return label;
    }

    /** Field names this workbench accepts, taken from its update command. */
    public Set<String> writableFields() {
        Set<String> fields = new LinkedHashSet<>();
        for (BeanPropertyDefinition property : mapper.getDeserializationConfig()
                .introspect(mapper.constructType(commandType))
                .findProperties()) {
            if (!"version".equals(property.getName())) fields.add(property.getName());
        }
        return fields;
    }

    @Override
    public WorkbenchSnapshot read(Long projectId, Long nodeId) {
        Map<String, Object> document = document(projectId, nodeId);
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, String> fieldLabels = new LinkedHashMap<>();
        for (String field : writableFields()) {
            values.put(field, document.get(field));
            fieldLabels.put(field, labels.field(key, field));
        }
        return WorkbenchSnapshot.of(values, fieldLabels, asInteger(document.get("version")));
    }

    @Override
    public Object write(Long projectId, Long nodeId, Map<String, Object> patch) {
        Map<String, Object> document = document(projectId, nodeId);
        Map<String, Object> merged = new LinkedHashMap<>();
        for (String field : writableFields()) {
            merged.put(field, document.get(field));
        }
        merged.put("version", asInteger(document.get("version")));
        merged.putAll(patch);
        return saver.save(projectId, nodeId, mapper.convertValue(merged, commandType));
    }

    private Map<String, Object> toMap(Object document) {
        if (document == null) return new LinkedHashMap<>();
        return mapper.convertValue(document, MAP_TYPE);
    }

    /** Reads the response, then descends into the nested document when configured. */
    private Map<String, Object> document(Long projectId, Long nodeId) {
        Map<String, Object> root = toMap(reader.apply(projectId, nodeId));
        if (documentPath == null) return root;
        return toMap(root.get(documentPath));
    }

    private static Integer asInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }
}
