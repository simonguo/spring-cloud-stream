/*
 * Copyright 2019-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.stream.binder.kafka.streams.function;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.PostConstruct;

import org.springframework.cloud.stream.binder.kafka.streams.KafkaStreamsFunctionProcessor;
import org.springframework.cloud.stream.function.StreamFunctionProperties;
import org.springframework.core.ResolvableType;
import org.springframework.util.StringUtils;

/**
 * @author Soby Chacko
 * @since 2.1.0
 */
public class KafkaStreamsFunctionProcessorInvoker {

	private final KafkaStreamsFunctionProcessor kafkaStreamsFunctionProcessor;
	private final Map<String, ResolvableType> resolvableTypeMap;
	private final KafkaStreamsBindableProxyFactory[] kafkaStreamsBindableProxyFactories;
	private final Map<String, Method> methods;
	private final StreamFunctionProperties streamFunctionProperties;

	public KafkaStreamsFunctionProcessorInvoker(Map<String, ResolvableType> resolvableTypeMap,
												KafkaStreamsFunctionProcessor kafkaStreamsFunctionProcessor,
												KafkaStreamsBindableProxyFactory[] kafkaStreamsBindableProxyFactories,
												Map<String, Method> methods, StreamFunctionProperties streamFunctionProperties) {
		this.kafkaStreamsFunctionProcessor = kafkaStreamsFunctionProcessor;
		this.resolvableTypeMap = resolvableTypeMap;
		this.kafkaStreamsBindableProxyFactories = kafkaStreamsBindableProxyFactories;
		this.methods = methods;
		this.streamFunctionProperties = streamFunctionProperties;
	}

	@PostConstruct
	void invoke() {
		final String definition = streamFunctionProperties.getDefinition();
		final String[] functionUnits = StringUtils.hasText(definition) ? definition.split(";") : new String[]{};

		if (functionUnits.length == 0) {
						resolvableTypeMap.forEach((key, value) -> {
				Optional<KafkaStreamsBindableProxyFactory> proxyFactory =
						Arrays.stream(kafkaStreamsBindableProxyFactories).filter(p -> p.getFunctionName().equals(key)).findFirst();
				this.kafkaStreamsFunctionProcessor.setupFunctionInvokerForKafkaStreams(value, key, proxyFactory.get(), methods.get(key), null);
			});
		}

		for (String functionUnit : functionUnits) {
			if (functionUnit.contains("|")) {
				final String[] composedFunctions = functionUnit.split("\\|");
				String[] derivedNameFromComposed = new String[]{""};
				for (String split : composedFunctions) {
					derivedNameFromComposed[0] = derivedNameFromComposed[0].concat(split);
				}
				Optional<KafkaStreamsBindableProxyFactory> proxyFactory =
						Arrays.stream(kafkaStreamsBindableProxyFactories).filter(p -> p.getFunctionName().equals(derivedNameFromComposed[0])).findFirst();
				proxyFactory.ifPresent(kafkaStreamsBindableProxyFactory ->
						this.kafkaStreamsFunctionProcessor.setupFunctionInvokerForKafkaStreams(resolvableTypeMap.get(composedFunctions[0]),
						derivedNameFromComposed[0], kafkaStreamsBindableProxyFactory, methods.get(derivedNameFromComposed[0]), resolvableTypeMap.get(composedFunctions[composedFunctions.length - 1]), composedFunctions));
			}
			else {
				Optional<KafkaStreamsBindableProxyFactory> proxyFactory =
						Arrays.stream(kafkaStreamsBindableProxyFactories).filter(p -> p.getFunctionName().equals(functionUnit)).findFirst();
				proxyFactory.ifPresent(kafkaStreamsBindableProxyFactory ->
						this.kafkaStreamsFunctionProcessor.setupFunctionInvokerForKafkaStreams(resolvableTypeMap.get(functionUnit), functionUnit,
						kafkaStreamsBindableProxyFactory, methods.get(functionUnit), null));
			}
		}
	}
}
