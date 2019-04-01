/*
 * Copyright 2002-2012 the original author or authors.
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

package org.springframework.web.accept;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * A strategy for resolving the requested media types in a request.
 *
 * SpringMVC使用ContentNegotiationStrategy来判定用户请求希望得到什么样格式的数据
 * ContentNegotiationStrategy通过三种方式来识别用户想要返回什么样的数据
 * 1、通过请求URL后缀
 *    http://myserver/myapp/list.html返回HTML格式
 * 2、通过请求的参数
 *    http://myserver/myapp/list?format=xls该设置默认不开启，默认key是format
 * 3、通过HTTP header的Accpet
 *    Accept:application/xml
 *
 * @author Rossen Stoyanchev
 * @since 3.2
 */
public interface ContentNegotiationStrategy {

	/**
	 * Resolve the given request to a list of media types. The returned list is
	 * ordered by specificity first and by quality parameter second.
	 *
	 * @param webRequest the current request
	 * @return the requested media types or an empty list, never {@code null}
	 *
	 * @throws HttpMediaTypeNotAcceptableException if the requested media types cannot be parsed
	 */
	List<MediaType> resolveMediaTypes(NativeWebRequest webRequest) throws HttpMediaTypeNotAcceptableException;

}
