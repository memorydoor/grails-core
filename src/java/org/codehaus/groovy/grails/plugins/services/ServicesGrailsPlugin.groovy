    /*
 * Copyright 2004-2005 the original author or authors.
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
package org.codehaus.groovy.grails.plugins.services

import org.springframework.beans.factory.config.MethodInvokingFactoryBean
import org.codehaus.groovy.grails.commons.spring.TypeSpecifyableTransactionProxyFactoryBean
import org.codehaus.groovy.grails.commons.ServiceArtefactHandler
    import org.codehaus.groovy.grails.orm.support.GroovyAwareNamedTransactionAttributeSource
    import org.codehaus.groovy.grails.commons.GrailsServiceClass
    import org.springframework.transaction.annotation.Transactional
    import org.springframework.core.annotation.AnnotationUtils
    import java.lang.reflect.Method

    /**
 * A plug-in that configures services in the spring context 
 * 
 * @author Graeme Rocher
 * @since 0.4
 */
class ServicesGrailsPlugin {
	
	def version = grails.util.GrailsUtil.getGrailsVersion()
	def loadAfter = ['hibernate']
	                 
    def watchedResources = ["file:./grails-app/services/**/*Service.groovy",
							"file:./plugins/*/grails-app/services/**/*Service.groovy"]

	                 
	def doWithSpring = {
        xmlns tx:"http://www.springframework.org/schema/tx"
        tx.'annotation-driven'()

		for(serviceGrailsClass in application.serviceClasses) {
            GrailsServiceClass serviceClass = serviceGrailsClass

		    def scope = serviceClass.getPropertyValue("scope")

			"${serviceClass.fullName}ServiceClass"(MethodInvokingFactoryBean) {
				targetObject = ref("grailsApplication", true)
				targetMethod = "getArtefact"
				arguments = [ServiceArtefactHandler.TYPE, serviceClass.fullName]
			}

			def hasDataSource = (application.config?.dataSource || application.domainClasses.size() > 0)
			if(hasDataSource && shouldCreateTransactionalProxy(serviceClass)) {
				def props = new Properties()
				props."*"="PROPAGATION_REQUIRED"
				"${serviceClass.propertyName}"(TypeSpecifyableTransactionProxyFactoryBean, serviceClass.clazz) { bean ->
				    if(scope) bean.scope = scope
					target = { innerBean ->   
						innerBean.factoryBean = "${serviceClass.fullName}ServiceClass"
						innerBean.factoryMethod = "newInstance"
						innerBean.autowire = "byName"
						if(scope) innerBean.scope = scope
					}
					proxyTargetClass = true
					transactionAttributeSource = new GroovyAwareNamedTransactionAttributeSource(transactionalAttributes:props)
					transactionManager = ref("transactionManager")					       
				}
			}
			else {
				"${serviceClass.propertyName}"(serviceClass.getClazz()) { bean ->
					bean.autowire =  true
                    if(scope) {
                        bean.scope = scope
                    }

				}
			}
		}
	}

    def shouldCreateTransactionalProxy(GrailsServiceClass serviceClass) {
        Class javaClass = serviceClass.clazz

        try {
            serviceClass.transactional &&
              !AnnotationUtils.findAnnotation(javaClass, Transactional) &&
                 !javaClass.methods.any { Method m -> AnnotationUtils.findAnnotation(m, Transactional)!=null }
        }
        catch (e) {
            return false
        }
    }
	def onChange = { event ->
		if(event.source) {
			def serviceClass = application.addArtefact(ServiceArtefactHandler.TYPE, event.source)
			def serviceName = "${serviceClass.propertyName}"
            def scope = serviceClass.getPropertyValue("scope")


			if(serviceClass.transactional && event.ctx.containsBean("transactionManager")) {
				def beans = beans {                 
					"${serviceClass.fullName}ServiceClass"(MethodInvokingFactoryBean) {
						targetObject = ref("grailsApplication", true)
						targetMethod = "getArtefact"
						arguments = [ServiceArtefactHandler.TYPE, serviceClass.fullName]
					}									
					def props = new Properties()
					props."*"="PROPAGATION_REQUIRED"
					"${serviceName}"(TypeSpecifyableTransactionProxyFactoryBean, serviceClass.clazz) { bean ->
                        if(scope) bean.scope = scope
                        target = { innerBean ->
                            innerBean.factoryBean = "${serviceClass.fullName}ServiceClass"
                            innerBean.factoryMethod = "newInstance"
                            innerBean.autowire = "byName"
                            if(scope) innerBean.scope = scope
                        }
						proxyTargetClass = true
                        transactionAttributeSource = new GroovyAwareNamedTransactionAttributeSource(transactionalAttributes:props)
						transactionManager = ref("transactionManager")
					}
				}     
				if(event.ctx) {         
                    beans.registerBeans(event.ctx)
				}				
			}
			else {
			                                             
				def beans = beans {
					"$serviceName"(serviceClass.getClazz()) { bean ->
						bean.autowire =  true
                        if(scope) {
                            bean.scope = scope
                        }
					}					
				}
	            beans.registerBeans(event.ctx)
			}
		}
	}
}