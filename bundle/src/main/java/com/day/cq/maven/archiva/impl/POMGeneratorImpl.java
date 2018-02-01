package com.day.cq.maven.archiva.impl;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Enumeration;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import com.day.cq.maven.archiva.MessageFormatProvider;
import com.day.cq.maven.archiva.POMGenerator;

/**
 * 
 * @author tyge
 */
@Service(value=com.day.cq.maven.archiva.POMGenerator.class)
@Component(immediate=true,metatype=true)
@Properties({
	@Property(name="service.description",value="POM Generator",propertyPrivate=true)
})

public class POMGeneratorImpl implements POMGenerator {

	@Reference
    private MessageFormatProvider mfp;

    //private static MessageFormat parentMessage = null;

    /**
     * @param bctxt
     * @param out
     * @param includeVersionScope
     * @throws IOException
     */
    public void generatePOM(BundleContext bctxt, PrintWriter out, String lookupVersion, boolean includeVersionScope) throws IOException {
        StringBuffer dependencies = new StringBuffer();
        ArrayList<String> alreadyAdded = new ArrayList<String>(); 
        for (Bundle bundle : bctxt.getBundles()) {
            Enumeration<?> enumResources = bundle.findEntries("META-INF", "pom.properties", true);
            if (enumResources != null) {
                while (enumResources.hasMoreElements()) {
                    URL resource = (URL) enumResources.nextElement();
                    java.util.Properties props = new java.util.Properties();
                    props.load(resource.openStream());

                    String version = props.get("version").toString();
                    String artifactId = props.get("artifactId").toString();
                    String groupId = props.get("groupId").toString();
					String key = groupId+"."+artifactId+"."+version;
                    if (!alreadyAdded.contains(key)) {
                        dependencies.append("<dependency>\n");
                        dependencies.append("<groupId>" + groupId + "</groupId>\n");
                        dependencies.append("<artifactId>" + artifactId + "</artifactId>\n");
                        if (includeVersionScope) {
                            dependencies.append("<version>" + version + "</version>\n");
                            dependencies.append("<scope>provided</scope>\n");
                        }
                        dependencies.append("</dependency>\n");
						alreadyAdded.add(key);
                    }
                }
            }
        }
        out.print(getParentMessage().format(new Object[] { lookupVersion, dependencies.toString() }));
        out.flush();
    }

    private MessageFormat getParentMessage() {
        return mfp.getMessage("archiva-parent-pom.xml");
    }

}
