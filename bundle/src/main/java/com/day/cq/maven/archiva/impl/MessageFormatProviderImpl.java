package com.day.cq.maven.archiva.impl;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Collections;

import javax.jcr.Session;

import org.apache.commons.io.IOUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.jcr.api.SlingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.maven.archiva.MessageFormatProvider;

/**
 * 
 * @author tyge
 */
@Service(value=com.day.cq.maven.archiva.MessageFormatProvider.class)
@Component(immediate=true,metatype=true)
@Properties({
	@Property(name="service.description",value="Message Format Provider",propertyPrivate=true)
})
public class MessageFormatProviderImpl implements MessageFormatProvider {
	/**
	 * The logger used in this class.
	 */
	private static final Logger LOGGER = LoggerFactory
			.getLogger(MessageFormatProviderImpl.class);

    private final static String ARCHIVA_SERVICE = "archiva-service";

	@Reference
	private SlingRepository repository;

	@Reference
	private ResourceResolverFactory resourceResolverFactory;
	
    /** The resource resolver. */
    ResourceResolver resourceResolver = null;


	public MessageFormat getMessage(String filename) {
		String tpl = "";
		try {
			ResourceResolver resolver = getArchivaServiceResolver();
			Session session = resolver.adaptTo(Session.class);
			InputStream msg = resolver.getResource("/var/maven/tpl/" + filename)
					.adaptTo(InputStream.class);
			if (msg != null) {
				tpl = IOUtils.toString(msg);
			}
			session.logout();
		} catch (IOException e) {
			LOGGER.error(e.getMessage());
		}
		
		return new MessageFormat(tpl);
	}
	
	
    protected ResourceResolver getArchivaServiceResolver(){
        
        if((resourceResolver == null) || ! resourceResolver.isLive()){
            try {
                resourceResolver = resourceResolverFactory.getServiceResourceResolver(
                        Collections.singletonMap(ResourceResolverFactory.SUBSERVICE,
                                (Object) ARCHIVA_SERVICE));
            } catch (LoginException e) {

                LOGGER.error(e.getLocalizedMessage(),e);
            }
 
        }
        return resourceResolver;
    }
}
