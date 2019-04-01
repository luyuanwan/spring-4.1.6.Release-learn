/*
 * Copyright 2002-2014 the original author or authors.
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

package org.springframework.core.env;

import java.util.Map;

import org.springframework.util.StringUtils;

/**
 * {@link PropertySource} that reads keys and values from a {@code Map} object.
 *
 * Map就是一种特殊的可枚举的属性源，从代码看我估计作者就是这么思考的
 * 因为里面存放了属性信息，所以称为Source（源），作者的思想有点神奇的
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @since 3.1
 * @see PropertiesPropertySource
 */
public class MapPropertySource extends EnumerablePropertySource<Map<String, Object>> {

	/**
	 * name -> source
	 * @param name
	 * @param source
     */
	public MapPropertySource(String name, Map<String, Object> source) {
		super(name, source);
	}


	/**
	 * 由于Source是个Map，所以可以get
	 *
	 * @param name the property to find
	 * @return
     */
	@Override
	public Object getProperty(String name) {
		return this.source.get(name);
	}

	/**
	 * 判定是否包含属性
	 *
	 * @param name the name of the property to find
	 * @return
     */
	@Override
	public boolean containsProperty(String name) {
		return this.source.containsKey(name);
	}

	/**
	 * 拿到属性名List
	 *
	 * @return
     */
	@Override
	public String[] getPropertyNames() {
		return StringUtils.toStringArray(this.source.keySet());
	}

}
