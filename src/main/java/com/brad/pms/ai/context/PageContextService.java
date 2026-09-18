package com.brad.pms.ai.context;

import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class PageContextService {

    private final Map<PageContextType, PageContextAssembler> assemblers;

    public PageContextService(List<PageContextAssembler> assemblers) {
        EnumMap<PageContextType, PageContextAssembler> indexed = new EnumMap<>(PageContextType.class);
        for (PageContextAssembler assembler : assemblers) {
            PageContextAssembler previous = indexed.put(assembler.supports(), assembler);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate page context assembler: " + assembler.supports());
            }
        }
        this.assemblers = Map.copyOf(indexed);
    }

    public PageContextSnapshot assemble(PageContextRequest request) {
        if (request == null) throw new IllegalArgumentException("request must not be null");
        PageContextAssembler assembler = assemblers.get(request.pageType());
        if (assembler == null) {
            throw new IllegalArgumentException("Unsupported page context type: " + request.pageType());
        }
        return assembler.assemble(request);
    }
}
