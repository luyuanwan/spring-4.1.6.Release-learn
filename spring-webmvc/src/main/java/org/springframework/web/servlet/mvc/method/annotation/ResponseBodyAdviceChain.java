/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.servlet.mvc.method.annotation;

import java.util.List;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.util.CollectionUtils;
import org.springframework.web.method.ControllerAdviceBean;

/**
 * Invokes a a list of {@link ResponseBodyAdvice} beans.
 *
 * @author Rossen Stoyanchev
 * @since 4.1
 * HTTP返回时需要经过一系列转化，这些转化就包装在这里面
 */
class ResponseBodyAdviceChain {

	private final List<Object> advice;


	public ResponseBodyAdviceChain(List<Object> advice) {
		this.advice = advice;
	}


	public boolean hasAdvice() {
		return !CollectionUtils.isEmpty(this.advice);
	}


	/**
	 * HTTP返回时需要经过一系列转化，这些转化就包装在这个函数里面（我感觉这像一种增强）
	 *
	 * @param body
	 * @param returnType
	 * @param selectedContentType
	 * @param selectedConverterType
	 * @param request
	 * @param response
     * @param <T>
     * @return
     */
	@SuppressWarnings("unchecked")
	public <T> T invoke(T body/**body体*/, MethodParameter returnType/**返回类型*/,
			MediaType selectedContentType/**选择的媒体类型*/, Class<? extends HttpMessageConverter<?>> selectedConverterType,
			ServerHttpRequest request, ServerHttpResponse response) {

		if (this.advice != null) {
			for (Object advice : this.advice) {
				//如果是ControllerAdviceBean
				if (advice instanceof ControllerAdviceBean) {
					//强转成ControllerAdviceBean
					ControllerAdviceBean adviceBean = (ControllerAdviceBean) advice;
					if (!adviceBean.isApplicableToBeanType(returnType.getContainingClass())) {
						continue;
					}
					advice = adviceBean.resolveBean();
				}
				if (advice instanceof ResponseBodyAdvice) {
					ResponseBodyAdvice<T> typedAdvice = (ResponseBodyAdvice<T>) advice;
					if (typedAdvice.supports(returnType, selectedConverterType)) {
						body = typedAdvice.beforeBodyWrite(body, returnType,
								selectedContentType, selectedConverterType, request, response);
					}
				}
				else {
					throw new IllegalStateException("Expected ResponseBodyAdvice: " + advice);
				}
			}
		}
		return body;
	}

}
