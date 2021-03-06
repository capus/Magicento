package com.magicento.extensions;

//import com.magicento.helpers.MagentoConfigXml;
import com.magicento.MagicentoProjectComponent;
import com.magicento.MagicentoSettings;
import com.magicento.helpers.XmlHelper;
import com.magicento.models.xml.MagentoXml;
import com.magicento.models.xml.MagentoXmlFactory;
import com.magicento.models.xml.MagentoXmlTag;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.IconLoader;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * completion contributor for XML
 * @author Enrique Piatti
 */
public class MagicentoXmlCompletionContributor extends CompletionContributor {

    private static final String INTELLIJ_IDEA_RULEZZZ = "IntellijIdeaRulezzz ";
    private static final Pattern SUFFIX_PATTERN = Pattern.compile("^(.*)\\b(\\w+)$");

    private static final Icon myIcon = IconLoader.getIcon("magento.png");


    private static final InsertHandler<LookupElement> INSERT_HANDLER = new InsertHandler<LookupElement>()
    {
        public void handleInsert(InsertionContext context, LookupElement item)
        {
            //String stringInserted = (String) item.getObject();
            context.commitDocument();
            final Editor editor = context.getEditor();
            final Document document = editor.getDocument();
            PsiFile psiFile = context.getFile();

            //int tailOffset = editor.getCaretModel().getOffset();
            int tailOffset = context.getTailOffset();
            int startOffset = context.getStartOffset();

            // put caret in the middle (inside the new tags)
            // editor.getCaretModel().moveToOffset((int) (startOffset + (tailOffset - startOffset)*0.5));
            int pos = document.getText().indexOf('>', startOffset);
            if(pos != -1){
                editor.getCaretModel().moveToOffset(pos+1);
            }


            PsiElement psiElement = psiFile.findElementAt(startOffset);
            if(psiElement != null){
                ASTNode node = psiElement.getNode();
                XmlTag parentElement = PsiTreeUtil.getParentOfType(psiElement, XmlTag.class);
                if(parentElement != null){
                    psiElement = parentElement;
                    XmlTag grandParentElement = parentElement.getParentTag();
                    if(grandParentElement != null){
                        psiElement = grandParentElement;
                    }
                }

                CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(context.getProject());
                //codeStyleManager.reformatText(psiFile, startOffset, tailOffset);
                codeStyleManager.reformat(psiElement);
            }

        }

    };

    public MagicentoXmlCompletionContributor() {
        final PsiElementPattern.Capture<PsiElement> everywhere = PlatformPatterns.psiElement();
        // otroPattern = XmlPatterns.psiElement().inside(XmlPatterns.xmlAttributeValue());
        extend(CompletionType.BASIC, everywhere, new CompletionProvider<CompletionParameters>() {
            public void addCompletions(@NotNull final CompletionParameters parameters,
                                       final ProcessingContext matchingContext,
                                       @NotNull final CompletionResultSet _result) {


                if(parameters == null || ! MagicentoProjectComponent.isEnabled(parameters.getOriginalFile().getProject())) {
                    return;
                }


                //final PsiFile file = parameters.getOriginalFile();
                //String fileName = file./*getVirtualFile().*/getName();
                //final int startOffset = parameters.getOffset();

                final PsiElement currentElement = parameters.getPosition();   // matchedElement?
                MagentoXml magentoXml = MagentoXmlFactory.getInstance(currentElement);

                if(magentoXml == null){
                    return;
                }


                XmlAttribute attribute = PsiTreeUtil.getParentOfType(currentElement, XmlAttribute.class, false);
                boolean isAttribute = attribute != null;

                if(isAttribute){
                    //final XmlAttributeValue attributeValue = PsiTreeUtil.getParentOfType(currentElement, XmlAttributeValue.class, false);
                    // TODO: add code completion for attributes
                    return;
                }


//                final PsiElement psiElement = PsiTreeUtil.getParentOfType(currentElement, XmlTag.class, XmlAttributeValue.class);
//                if(psiElement == null){
//                    return;
//                }


                final String prefix = _result.getPrefixMatcher().getPrefix();

                CompletionResultSet result = _result;

                // force prefix to begin with "<" if it's a new tag
                PsiElement prevSibling = currentElement.getPrevSibling();
                if( prevSibling != null && ! prefix.startsWith("<") )
                {
                    if( prevSibling.getText().equals("<") ||
                        prevSibling.getNode().getElementType().toString() == "XML_START_TAG_START" )
                    {
                        result = _result.withPrefixMatcher("<"+prefix);
                    }
                }

                //MagentoXmlTag matchedTag = MagentoConfigXml.getInstance().getMatchedTag(currentElement);
                MagentoXmlTag matchedTag = magentoXml.getMatchedTag(currentElement);
                if(matchedTag != null){

                    Map<String, String> items = new LinkedHashMap<String, String>();    // we are using LinkedHashMap to preserve order of insertion

                    List<MagentoXmlTag> children = matchedTag.getChildren();
                    boolean hasChildren = children != null && children.size() > 0;
                    if(hasChildren){
                        for(MagentoXmlTag child : children){
                            // each children tag can have multiple definitions (for example Id tags)
                            Map<String,String> childItems = child.getPossibleDefinitions();
                            if(childItems != null){
                                items.putAll(childItems);
                            }
                        }
                    }
                    else {          // it's a leaf node, show the possible values instead of children
                        Map<String, String> values = matchedTag.getPossibleValues();
                        if(values != null){
                            items.putAll(values);
                        }
                    }

                    if( ! items.isEmpty()){

                        // jIdea is sorting the completion items in lexicographic order (we need a CompletionWeigher for changing that) or use something like PrioritizedLookupElement
                        int count = 0;
                        int addedElementsToResult = 0;
                        for(Map.Entry<String, String> entry : items.entrySet())
                        {
                            count++;
                            String name = entry.getKey();
                            String value = entry.getValue();
                            XmlTag newTag = XmlElementFactory.getInstance(currentElement.getProject()).createTagFromText(value);
                            // prefix doesn't contain the initial '<'
                            //if( true || prefix == null || prefix.isEmpty() || value.startsWith(prefix, 1)){
                                // value = prefix == null || prefix.isEmpty() ? value : value.substring(1);
                                // TODO: create a new class extending LookupElement?
//                                LookupElement element = LookupElementBuilder.create(value)
                                // if this is a leaf node we are passing an empty string as the Object for the lookupElement because
                                // we are using that object in MagicentoXmlDocumentationProvider (and we don't need it for leaf nodes), this is really ugly
                                LookupElement element = LookupElementBuilder.create(hasChildren ? name : "" /*newTag*/, value)
                                        .setPresentableText(name)
                                        .setIcon(myIcon)
                                        //.addLookupString(value.substring(1))
                                        .setInsertHandler(INSERT_HANDLER)
                                        ;

                                LookupElement elementWithPriority = PrioritizedLookupElement.withPriority(element, -count);

                                //element.renderElement();
                                //element.handleInsert();
                                //_result.addElement(element);
                                result.addElement(elementWithPriority);
                                addedElementsToResult++;
                            //}

                        }
                        // prevents extra codeCompletion from jIDEA (PHPStorm) because it's confusing and they are appearing first !
                        // anyway, we have a custom magento icon for differentiating it
                        if(addedElementsToResult > 5){
                            _result.stopHere();
                            result.stopHere();
                        }

                    }
                }

            }
        });
    }


    @Override
    public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {
        super.fillCompletionVariants(parameters, result);    //To change body of overridden methods use File | Settings | File Templates.
    }

    // Ejemplo de como podriamos hacerlo con fillCompletionVariants en vez de registrar un CompletionProvider con extend en el constructor
//    public boolean fillCompletionVariants(final CompletionParameters parameters, final CompletionResultSet result, String nada)
//    {
//        final PsiElement position = parameters.getPosition();
//        if ( ! XmlHelper.isXmlFile( parameters.getOriginalFile() ) ) {
//            return true;
//        }
//
//        if (parameters.getCompletionType() == CompletionType.BASIC && position instanceof XmlToken)
//        {
//            ApplicationManager.getApplication().runReadAction(new Runnable() {
//                public void run() {
//                    completeResults(position, result);
//                }
//            });
//            return false;
//        }
//        return true;
//    }

    private void completeResults(PsiElement position, CompletionResultSet result)
    {
        XmlToken token = (XmlToken) position;
        final PsiElement parent = token.getParent();

        if (validAttribute(token, parent)) {
            XmlAttribute attribute = (XmlAttribute) parent;
            PsiElement tag = attribute.getParent();

            if (XmlHelper.isXmlTag(tag)) {
                completeWithAttributeNames(result, parent);
            }
        } else if (validAttributeValue(token, parent)) {
            completeWithTagNames((XmlAttributeValue) parent, result);
        }
    }

    private boolean validAttributeValue(XmlToken token, PsiElement parent)
    {
        return token.getTokenType().toString().equals("XML_ATTRIBUTE_VALUE_TOKEN") && parent instanceof XmlAttributeValue;
    }

    private boolean validAttribute(XmlToken token, PsiElement parent)
    {
        return token.getTokenType().toString().equals("XML_NAME") && parent instanceof XmlAttribute;
    }


    private void completeWithTagNames(XmlAttributeValue attributeValue, CompletionResultSet result)
    {
        String suffix = null;
        String baseExpression = getValueLeftOfCursor(attributeValue);
        Matcher suffixMatcher = SUFFIX_PATTERN.matcher(baseExpression);
        if (suffixMatcher.matches()) {
            baseExpression = suffixMatcher.group(1);
            suffix = suffixMatcher.group(2);
        }

        List<String> displayValues = null;//XcordionReflectionUtils.getDisplayValues(attributeValue, suffix, baseExpression);

        for (String displayValue : displayValues) {
            result.addElement(new LookupItem<String>(displayValue, displayValue));
        }
    }

    private String getValueLeftOfCursor(PsiElement psiElement)
    {
        return psiElement.getText().substring(1, psiElement.getText().indexOf(INTELLIJ_IDEA_RULEZZZ));
    }

    private void completeWithAttributeNames(CompletionResultSet result, PsiElement parent)
    {
       /* for (XcordionNamespace namespace : XcordionNamespace.values()) {
            String namespacePrefix = ((XmlTag) parent.getParent().getParent()).getPrefixByNamespace(namespace.getNamespace());
            if (namespacePrefix == null) {
                continue;
            }
            for (XcordionAttribute xattribute : namespace.getAttributes()) {
                String attributeName = namespacePrefix + ":" + xattribute.getLocalName();
                result.addElement(new AttributeLookupItem(attributeName, attributeName));
            }
        } */
    }

    private static class AttributeLookupItem extends LookupItem<String>
    {
        public AttributeLookupItem(String attributeName, String attributeName1) {
            super(attributeName, attributeName1);
        }

//        @Override
//        public XmlAttributeInsertHandler<AttributeLookupItem> getInsertHandler() {
//            return new XmlAttributeInsertHandler<AttributeLookupItem>();
//        }

//        @Override
//        public InsertHandler<? extends LookupItem> getInsertHandler() {
//            return new XmlAttributeInsertHandler<AttributeLookupItem>();
//        }
    }
}
