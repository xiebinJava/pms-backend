package com.brad.pms.controller;

import com.brad.pms.ai.context.PageContextRequest;
import com.brad.pms.ai.context.PageContextService;
import com.brad.pms.ai.context.PageContextSnapshot;
import com.brad.pms.common.response.ResponseResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/context")
@RequiredArgsConstructor
public class AiContextController {

    private final PageContextService pageContextService;

    @PostMapping("/inspect")
    public ResponseResult<PageContextSnapshot> inspect(@RequestBody PageContextRequest request) {
        return ResponseResult.success(pageContextService.assemble(request));
    }
}
