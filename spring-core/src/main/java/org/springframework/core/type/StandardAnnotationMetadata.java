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

package org.springframework.core.type;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.MultiValueMap;

/**
 * {@link AnnotationMetadata} implementation that uses standard reflection
 * to introspect a given {@link Class}.
 *
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @author Chris Beams
 * @author Phillip Webb
 * @since 2.5
 * 标准的注解元数据，从反射中读取元数据
 * 继承自标准的类元数据，所以能获取到所有的类元数据
 * 实现了注解元数据接口，所以能获取到所有的注解元数据
 */
public class StandardAnnotationMetadata extends StandardClassMetadata implements AnnotationMetadata {

	private final boolean nestedAnnotationsAsMap;


	/**
	 * Create a new {@code StandardAnnotationMetadata} wrapper for the given Class.
	 * @param introspectedClass the Class to introspect
	 * @see #StandardAnnotationMetadata(Class, boolean)
	 */
	public StandardAnnotationMetadata(Class<?> introspectedClass) {
		this(introspectedClass, false);
	}

	/**
	 * Create a new {@link StandardAnnotationMetadata} wrapper for the given Class,
	 * providing the option to return any nested annotations or annotation arrays in the
	 * form of {@link org.springframework.core.annotation.AnnotationAttributes} instead
	 * of actual {@link Annotation} instances.
	 * @param introspectedClass the Class to introspect
	 * @param nestedAnnotationsAsMap return nested annotations and annotation arrays as
	 * {@link org.springframework.core.annotation.AnnotationAttributes} for compatibility
	 * with ASM-based {@link AnnotationMetadata} implementations
	 * @since 3.1.1
	 */
	public StandardAnnotationMetadata(Class<?> introspectedClass, boolean nestedAnnotationsAsMap) {
		super(introspectedClass);
		this.nestedAnnotationsAsMap = nestedAnnotationsAsMap;
	}


	/**
	 * 拿到类下面所有的注解类型
	 * @return
     */
	@Override
	public Set<String> getAnnotationTypes() {
		// 类下面所有的注解类型
		Set<String> types = new LinkedHashSet<String>();

		// 拿到所有注解
		Annotation[] anns = getIntrospectedClass().getAnnotations();
		for (Annotation ann : anns) {
			// 遍历每一个注解，拿到名字后，添加到set容器中
			types.add(ann.annotationType().getName());
		}
		return types;
	}

	@Override
	public Set<String> getMetaAnnotationTypes(String annotationType) {
		return AnnotatedElementUtils.getMetaAnnotationTypes(getIntrospectedClass(), annotationType);
	}

	/**
	 * 是否含有指定的注解
	 * @param annotationType the annotation type to look for
	 * @return
     */
	@Override
	public boolean hasAnnotation(String annotationType) {
		// 拿到所有的注解
		Annotation[] anns = getIntrospectedClass().getAnnotations();
		// 遍历每一个注解
		for (Annotation ann : anns) {
			// 如果其中有一个注解的名字和给定的名字相同，则表示含有该注解
			if (ann.annotationType().getName().equals(annotationType)) {
				return true;
			}
		}
		//都没有，则表示没有该注解
		return false;
	}

	@Override
	public boolean hasMetaAnnotation(String annotationType) {
		return AnnotatedElementUtils.hasMetaAnnotationTypes(getIntrospectedClass(), annotationType);
	}

	@Override
	public boolean isAnnotated(String annotationType) {
		return AnnotatedElementUtils.isAnnotated(getIntrospectedClass(), annotationType);
	}

	@Override
	public Map<String, Object> getAnnotationAttributes(String annotationType) {
		return this.getAnnotationAttributes(annotationType, false);
	}

	@Override
	public Map<String, Object> getAnnotationAttributes(String annotationType, boolean classValuesAsString) {
		return AnnotatedElementUtils.getAnnotationAttributes(getIntrospectedClass(),
				annotationType, classValuesAsString, this.nestedAnnotationsAsMap);
	}

	@Override
	public MultiValueMap<String, Object> getAllAnnotationAttributes(String annotationType) {
		return getAllAnnotationAttributes(annotationType, false);
	}

	@Override
	public MultiValueMap<String, Object> getAllAnnotationAttributes(String annotationType, boolean classValuesAsString) {
		return AnnotatedElementUtils.getAllAnnotationAttributes(getIntrospectedClass(),
				annotationType, classValuesAsString, this.nestedAnnotationsAsMap);
	}

	@Override
	public boolean hasAnnotatedMethods(String annotationType) {
		Method[] methods = getIntrospectedClass().getDeclaredMethods();
		for (Method method : methods) {
			if (!method.isBridge() && AnnotatedElementUtils.isAnnotated(method, annotationType)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Set<MethodMetadata> getAnnotatedMethods(String annotationType) {
		Method[] methods = getIntrospectedClass().getDeclaredMethods();
		Set<MethodMetadata> annotatedMethods = new LinkedHashSet<MethodMetadata>();
		for (Method method : methods) {
			if (!method.isBridge() && AnnotatedElementUtils.isAnnotated(method, annotationType)) {
				annotatedMethods.add(new StandardMethodMetadata(method, this.nestedAnnotationsAsMap));
			}
		}
		return annotatedMethods;
	}

}
