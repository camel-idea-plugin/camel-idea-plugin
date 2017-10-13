/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.idea.util;

import java.util.Arrays;
import java.util.Optional;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import static org.apache.camel.idea.util.IdeaUtils.isElementFromAnnotation;
import static org.apache.camel.idea.util.IdeaUtils.isElementFromConstructor;
import static org.apache.camel.idea.util.IdeaUtils.isElementFromSetterProperty;
import static org.apache.camel.idea.util.IdeaUtils.isFromFileType;
import static org.apache.camel.idea.util.IdeaUtils.isFromJavaMethodCall;

/**
 * Utility methods to work with Camel related {@link com.intellij.psi.PsiElement} elements.
 * <p/>
 * This class is only for Camel related IDEA APIs. If you need only IDEA APIs then use {@link IdeaUtils} instead.
 */
public final class CamelIdeaUtils {

    public static final String[] CAMEL_FILE_EXTENSIONS = {"java", "xml", "groovy", "kt", "scala"};

    private static final Logger LOG = Logger.getInstance(CamelIdeaUtils.class);

    private static final String[] ACCEPTED_NAMESPACES = new String[]{
        "http://camel.apache.org/schema/spring",
        "http://camel.apache.org/schema/blueprint",
        "http://www.springframework.org/schema/beans",
        "http://www.osgi.org/xmlns/blueprint"
    };

    private static final String[] ROUTE_START = new String[]{"from", "fromF"};
    private static final String[] CONSUMER_ENDPOINT = new String[]{"from", "fromF", "interceptFrom", "pollEnrich"};
    private static final String[] PRODUCER_ENDPOINT = new String[]{"to", "toF", "toD", "enrich", "interceptSendToEndpoint", "wireTap", "deadLetterChannel"};
    private static final String[] STRING_FORMAT_ENDPOINT = new String[]{"fromF", "toF", "format"};
    private static final String[] SIMPLE_PREDICATE = new String[]{"completion", "completionPredicate", "when", "onWhen", "handled", "continued", "retryWhile", "filter", "validate", "loopDoWhile"};

    private CamelIdeaUtils() {
    }

    /**
     * Is the given element from the start of a Camel route, eg <tt>from</tt>, ot &lt;from&gt;.
     */
    public static boolean isCamelRouteStart(PsiElement element) {
        // java method call
        if (IdeaUtils.isFromJavaMethodCall(element, true, ROUTE_START)) {
            return true;
        }
        // xml
        if (element.getText().equals("from")) {
            XmlTag xml = PsiTreeUtil.getParentOfType(element, XmlTag.class);
            if (xml != null) {
                String name = xml.getLocalName();
                XmlTag parentTag = xml.getParentTag();
                if (parentTag != null) {
                    return "from".equals(name) && "route".equals(parentTag.getLocalName());
                }
            }
        }
        // groovy
        if (element instanceof LeafPsiElement) {
            IElementType type = ((LeafPsiElement) element).getElementType();
            if (type.getLanguage().isKindOf("Groovy")) {
                return IdeaUtils.isFromGroovyMethod(element, ROUTE_START);
            }
        }
        // kotlin
        if (element instanceof LeafPsiElement) {
            IElementType type = ((LeafPsiElement) element).getElementType();
            if (type.getLanguage().isKindOf("kotlin")) {
                return IdeaUtils.isFromKotlinMethod(element, ROUTE_START);
            }
        }
        // scala
        if (element instanceof LeafPsiElement) {
            IElementType type = ((LeafPsiElement) element).getElementType();
            if (type.getLanguage().isKindOf("Scala")) {
                boolean isRouteViaMethod = IdeaUtils.isFromScalaMethod(element, ROUTE_START);
                return isRouteViaMethod  || isScalaArrowRouteDefinition(element);
            }
        }

        return false;
    }

    /**
     * For java methods tries to find if element is inside a camel route start expression,
     * otherwise delegates to {@link CamelIdeaUtils#isCamelRouteStart(PsiElement)}.
     * 
     * @param element
     * @return
     */
    public static boolean isCamelRouteStartExpression(PsiElement element) {
        // TODO: do parent search also for non java languages (not xml)?
        if (PsiTreeUtil.findFirstParent(element, true, CamelIdeaUtils::isCamelRouteStart) != null) {
            return true;
        }
        return isCamelRouteStart(element);
    }
    /**
     * Is this a camel route using using Scala DSL's '==>' method
     */
    private static boolean isScalaArrowRouteDefinition(PsiElement element) {
        IElementType type = ((LeafPsiElement) element).getElementType();
        if (type.toString().contains("string")) {
            Optional<PsiElement> infixExpression = Optional.ofNullable(element.getParent()).flatMap(e -> Optional.ofNullable(e.getParent()));
            return infixExpression.map(PsiElement::getChildren).filter(c -> c.length >= 2).map(c -> c[1].getText()).map(t -> t.equals("==>")).orElseGet(() -> false);
        }
        return false;
    }

    /**
     * Is the given element a simple of a Camel DSL, eg <tt>simple</tt> or &lt;simple&gt;, <tt>log</tt> or &lt;log&gt;.
     */
    public static boolean isCamelSimpleExpression(PsiElement element) {
        // java method call
        if (IdeaUtils.isFromJavaMethodCall(element, true, "simple", "log")) {
            return true;
        }
        // xml
        XmlTag xml;
        if (element instanceof XmlTag) {
            xml = (XmlTag) element;
        } else {
            xml = PsiTreeUtil.getParentOfType(element, XmlTag.class);
        }
        if (xml != null) {
            String name = xml.getLocalName();
            return "simple".equals(name) || "log".equals(name);
        }
        // groovy
        if (element instanceof LeafPsiElement) {
            IElementType type = ((LeafPsiElement) element).getElementType();
            if (type.getLanguage().isKindOf("Groovy")) {
                return IdeaUtils.isFromGroovyMethod(element, "simple", "log");
            }
        }
        // kotlin
        if (element instanceof LeafPsiElement) {
            IElementType type = ((LeafPsiElement) element).getElementType();
            if (type.getLanguage().isKindOf("kotlin")) {
                return IdeaUtils.isFromKotlinMethod(element, "simple");
            }
        }
        // scala
        if (element instanceof LeafPsiElement) {
            IElementType type = ((LeafPsiElement) element).getElementType();
            if (type.getLanguage().isKindOf("Scala")) {
                return IdeaUtils.isFromScalaMethod(element, "simple");
            }
        }

        return false;
    }

    /**
     * Is the given element a simple of a Camel route, eg <tt>simple</tt>, ot &lt;simple&gt;
     */
    public static boolean isCameSimpleExpressionUsedAsPredicate(PsiElement element) {

        // java
        PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
        if (call != null) {

            PsiMethod method = call.resolveMethod();
            if (method != null) {
                // if its coming from the log EIP then its not a predicate
                String name = method.getName();
                if ("log".equals(name)) {
                    return false;
                }
            }

            // okay dive into the psi and find out which EIP are using the simple
            PsiElement child = call.getFirstChild();
            if (child instanceof PsiReferenceExpression) {
                PsiExpression exp = ((PsiReferenceExpression) child).getQualifierExpression();
                if (exp == null) {
                    // okay it was not a direct method call, so see if it was passed in as a parameter instead (expression list)
                    element = element.getParent();
                    if (element instanceof PsiExpressionList) {
                        element = element.getParent();
                    }
                    if (element instanceof PsiMethodCallExpression) {
                        exp = PsiTreeUtil.getParentOfType(element.getParent(), PsiMethodCallExpression.class);
                    }
                }
                if (exp instanceof PsiMethodCallExpression) {
                    method = ((PsiMethodCallExpression) exp).resolveMethod();
                    if (method != null) {
                        String name = method.getName();
                        return Arrays.stream(SIMPLE_PREDICATE).anyMatch(name::equals);
                    }
                }
            }
            return false;
        }

        // xml
        XmlTag xml = PsiTreeUtil.getParentOfType(element, XmlTag.class);
        if (xml != null) {
            // if its coming from the log EIP then its not a predicate
            if (xml.getLocalName().equals("log")) {
                return false;
            }

            // special for loop which can be both expression or predicate
            if (IdeaUtils.hasParentXmlTag(xml, "loop")) {
                XmlTag parent = PsiTreeUtil.getParentOfType(xml, XmlTag.class);
                if (parent != null) {
                    String doWhile = parent.getAttributeValue("doWhile");
                    return "true".equalsIgnoreCase(doWhile);
                }
            }
            return Arrays.stream(SIMPLE_PREDICATE).anyMatch(n -> IdeaUtils.hasParentXmlTag(xml, n));
        }

        // groovy
        if (element instanceof LeafPsiElement) {
            IElementType type = ((LeafPsiElement) element).getElementType();
            if (type.getLanguage().isKindOf("Groovy")) {
                if (IdeaUtils.isFromGroovyMethod(element, "log")) {
                    // if its coming from the log EIP then its not a predicate
                    return false;
                }
                return IdeaUtils.isPrevSiblingFromGroovyMethod(element, SIMPLE_PREDICATE);
            }
        }
        // kotlin
        if (element instanceof LeafPsiElement) {
            IElementType type = ((LeafPsiElement) element).getElementType();
            if (type.getLanguage().isKindOf("kotlin")) {
                if (IdeaUtils.isFromKotlinMethod(element, "log")) {
                    // if its coming from the log EIP then its not a predicate
                    return false;
                }
                // TODO: need to do like in groovy prev sibling
                return IdeaUtils.isFromKotlinMethod(element, SIMPLE_PREDICATE);
            }
        }
        // scala
        if (element instanceof LeafPsiElement) {
            IElementType type = ((LeafPsiElement) element).getElementType();
            if (type.getLanguage().isKindOf("Scala")) {
                if (IdeaUtils.isFromScalaMethod(element, "log")) {
                    // if its coming from the log EIP then its not a predicate
                    return false;
                }
                return IdeaUtils.isPrevSiblingFromScalaMethod(element, SIMPLE_PREDICATE);
            }
        }

        return false;
    }

    /**
     * Is the given element from a consumer endpoint used in a route from a <tt>from</tt>, <tt>fromF</tt>,
     * <tt>interceptFrom</tt>, or <tt>pollEnrich</tt> pattern.
     */
    public static boolean isConsumerEndpoint(PsiElement element) {
        // java method call
        if (IdeaUtils.isFromJavaMethodCall(element, true, CONSUMER_ENDPOINT)) {
            return true;
        }
        // annotation
        PsiAnnotation annotation = PsiTreeUtil.getParentOfType(element, PsiAnnotation.class);
        if (annotation != null && annotation.getQualifiedName() != null) {
            return annotation.getQualifiedName().equals("org.apache.camel.Consume");
        }
        // xml
        XmlTag xml = PsiTreeUtil.getParentOfType(element, XmlTag.class);
        if (xml != null) {
            return IdeaUtils.hasParentXmlTag(xml, "pollEnrich")
                || IdeaUtils.isFromXmlTag(xml, "from", "interceptFrom");
        }
        // groovy
        if (element instanceof LeafPsiElement) {
            IElementType type = ((LeafPsiElement) element).getElementType();
            if (type.getLanguage().isKindOf("Groovy")) {
                return IdeaUtils.isFromGroovyMethod(element, CONSUMER_ENDPOINT);
            }
        }
        // kotlin
        if (element instanceof LeafPsiElement) {
            IElementType type = ((LeafPsiElement) element).getElementType();
            if (type.getLanguage().isKindOf("kotlin")) {
                return IdeaUtils.isFromKotlinMethod(element, CONSUMER_ENDPOINT);
            }
        }
        // scala
        if (element instanceof LeafPsiElement) {
            IElementType type = ((LeafPsiElement) element).getElementType();
            if (type.getLanguage().isKindOf("Scala")) {
                return IdeaUtils.isFromScalaMethod(element, CONSUMER_ENDPOINT);
            }
        }

        return false;
    }

    /**
     * Is the given element from a producer endpoint used in a route from a <tt>to</tt>, <tt>toF</tt>,
     * <tt>interceptSendToEndpoint</tt>, <tt>wireTap</tt>, or <tt>enrich</tt> pattern.
     */
    public static boolean isProducerEndpoint(PsiElement element) {
        // java method call
        if (IdeaUtils.isFromJavaMethodCall(element, true, PRODUCER_ENDPOINT)) {
            return true;
        }
        // annotation
        PsiAnnotation annotation = PsiTreeUtil.getParentOfType(element, PsiAnnotation.class);
        if (annotation != null && annotation.getQualifiedName() != null) {
            return annotation.getQualifiedName().equals("org.apache.camel.Produce");
        }
        // xml
        XmlTag xml = PsiTreeUtil.getParentOfType(element, XmlTag.class);
        if (xml != null) {
            return IdeaUtils.hasParentXmlTag(xml, "enrich")
                || IdeaUtils.isFromXmlTag(xml, "to", "interceptSendToEndpoint", "wireTap", "deadLetterChannel");
        }
        // groovy
        if (element instanceof LeafPsiElement) {
            IElementType type = ((LeafPsiElement) element).getElementType();
            if (type.getLanguage().isKindOf("Groovy")) {
                return IdeaUtils.isFromGroovyMethod(element, PRODUCER_ENDPOINT);
            }
        }
        // kotlin
        if (element instanceof LeafPsiElement) {
            IElementType type = ((LeafPsiElement) element).getElementType();
            if (type.getLanguage().isKindOf("kotlin")) {
                return IdeaUtils.isFromKotlinMethod(element, PRODUCER_ENDPOINT);
            }
        }
        // scala
        if (element instanceof LeafPsiElement) {
            IElementType type = ((LeafPsiElement) element).getElementType();
            if (type.getLanguage().isKindOf("Scala")) {
                return IdeaUtils.isFromScalaMethod(element, PRODUCER_ENDPOINT);
            }
        }

        return false;
    }

    /**
     * Is the given element from a method call named <tt>fromF</tt> or <tt>toF</tt>, or <tt>String.format</tt> which supports the
     * {@link String#format(String, Object...)} syntax and therefore we need special handling.
     */
    public static boolean isFromStringFormatEndpoint(PsiElement element) {
        // java method call
        if (IdeaUtils.isFromJavaMethodCall(element, false, STRING_FORMAT_ENDPOINT)) {
            return true;
        }
        // groovy
        if (element instanceof LeafPsiElement) {
            IElementType type = ((LeafPsiElement) element).getElementType();
            if (type.getLanguage().isKindOf("Groovy")) {
                return IdeaUtils.isFromGroovyMethod(element, STRING_FORMAT_ENDPOINT);
            }
        }
        // kotlin
        if (element instanceof LeafPsiElement) {
            IElementType type = ((LeafPsiElement) element).getElementType();
            if (type.getLanguage().isKindOf("kotlin")) {
                return IdeaUtils.isFromKotlinMethod(element, STRING_FORMAT_ENDPOINT);
            }
        }
        // scala
        if (element instanceof LeafPsiElement) {
            IElementType type = ((LeafPsiElement) element).getElementType();
            if (type.getLanguage().isKindOf("Scala")) {
                return IdeaUtils.isFromScalaMethod(element, STRING_FORMAT_ENDPOINT);
            }
        }

        return false;
    }

    /**
     * Is the class a Camel expression class
     *
     * @param clazz  the class
     * @return <tt>true</tt> if its a Camel expression class, <tt>false</tt> otherwise.
     */
    public static boolean isCamelExpressionOrLanguage(PsiClass clazz) {
        if (clazz == null) {
            return false;
        }
        String fqn = clazz.getQualifiedName();
        if ("org.apache.camel.Expression".equals(fqn)
            || "org.apache.camel.Predicate".equals(fqn)
            || "org.apache.camel.model.language.ExpressionDefinition".equals(fqn)
            || "org.apache.camel.builder.ExpressionClause".equals(fqn)) {
            return true;
        }
        // try implements first
        for (PsiClassType ct : clazz.getImplementsListTypes()) {
            PsiClass resolved = ct.resolve();
            if (isCamelExpressionOrLanguage(resolved)) {
                return true;
            }
        }
        // then fallback as extends
        for (PsiClassType ct : clazz.getExtendsListTypes()) {
            PsiClass resolved = ct.resolve();
            if (isCamelExpressionOrLanguage(resolved)) {
                return true;
            }
        }
        // okay then go up and try super
        return isCamelExpressionOrLanguage(clazz.getSuperClass());
    }

    /**
     * Certain elements should be skipped for endpoint validation such as ActiveMQ brokerURL property and others.
     */
    public static boolean skipEndpointValidation(PsiElement element) {
        if (isElementFromSetterProperty(element, "brokerURL")) {
            return true;
        }
        if (isElementFromConstructor(element, "ActiveMQConnectionFactory")) {
            return true;
        }
        if (isElementFromConstructor(element, "ActiveMQXAConnectionFactory")) {
            return true;
        }
        if (isElementFromAnnotation(element, "org.apache.camel.spi.UriEndpoint")) {
            return true;
        }
        if (isFromJavaMethodCall(element, false, "activeMQComponent")) {
            return true;
        }

        // only accept xml tags from namespaces we support
        XmlTag xml = PsiTreeUtil.getParentOfType(element, XmlTag.class);
        if (xml != null) {
            String ns = xml.getNamespace();
            // accept empty namespace which can be from testing
            boolean accepted = StringUtils.isEmpty(ns) || Arrays.stream(ACCEPTED_NAMESPACES).anyMatch(ns::contains);
            LOG.trace("XmlTag " + xml.getName() + " with namespace: " + ns + " is accepted namespace: " + accepted);
            return !accepted; // skip is the opposite
        }

        return false;
    }

    /**
     * Whether the element can be accepted for the annator or inspection.
     * <p/>
     * Some elements are too complex structured which we cannot support such as complex programming structures to concat string values together.
     *
     * @param element the element
     * @return <tt>true</tt> to accept, <tt>false</tt> to skip
     */
    public static boolean acceptForAnnotatorOrInspection(PsiElement element) {
        // skip XML limit on siblings
        boolean xml = isFromFileType(element, "xml");
        if (!xml) {
            // for programming languages you can have complex structures with concat which we dont support yet
            int siblings = countSiblings(element);
            if (siblings > 1) {
                // we currently only support one liners, so check how many siblings the element has (it has 1 with ending parenthesis which is okay)
                return false;
            }
        }
        return true;
    }

    /**
     * Count the number of siblings there are in the chain the element has
     *
     * @param element the element
     * @return number of siblings added up in the chain
     */
    public static int countSiblings(PsiElement element) {
        int count = 0;
        PsiElement sibling = element.getNextSibling();
        while (sibling != null) {
            count++;
            sibling = sibling.getNextSibling();
        }
        return count;
    }

}
