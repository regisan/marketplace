package com.exemplo.pedidos.adapters.in.web.security;

import com.exemplo.pedidos.application.Caller;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Injeta o {@link Caller} autenticado pelo {@link SyntheticTokenFilter} nos controllers. */
class CallerArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Caller.class.equals(parameter.getParameterType());
    }

    @Override
    public Caller resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Object caller = webRequest.getAttribute(SyntheticTokenFilter.CALLER_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (caller == null) {
            throw new IllegalStateException("Requisição sem chamador autenticado");
        }
        return (Caller) caller;
    }
}
