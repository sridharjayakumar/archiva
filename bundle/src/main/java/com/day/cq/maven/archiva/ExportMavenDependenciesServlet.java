package com.day.cq.maven.archiva;

import java.io.IOException;
import java.io.PrintWriter;

import javax.jcr.RepositoryException;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;

/**
 * 
 * @author tyge
 */
@Service(value=javax.servlet.Servlet.class)
@Component(immediate=true,metatype=false)
@Properties({
	@Property(name="sling.servlet.methods",value="GET",propertyPrivate=true),
	@Property(name="service.description",value="Export Maven Dependencies Servlet",propertyPrivate=true)
})
@SuppressWarnings("serial")
public class ExportMavenDependenciesServlet extends SlingSafeMethodsServlet {

    @Property(name="sling.servlet.paths",propertyPrivate=true)
    public static final String mavenRepositoryPath = "/maven/dependencies";

    private BundleContext bdlCxt = null;

    @Reference(policy=ReferencePolicy.STATIC)
	private POMGenerator pomGen;

    /**
     * inherited.
     * 
     * @see org.apache.sling.api.servlets.SlingSafeMethodsServlet#doGet(org.apache.sling.api.SlingHttpServletRequest,
     *      org.apache.sling.api.SlingHttpServletResponse) {@inheritDoc}
     */
    protected final void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
            throws IOException {
        doProcessRequest(request, response);
    }

    /**
     * @param req
     *            the current request to this servlet.
     * @param resp
     *            the current response for this servlet.
     * @throws RepositoryException
     *             if any error occurs while accessing repository.
     * @throws IOException
     *             if any error occurs while generating the output.
     */
    private void doProcessRequest(final SlingHttpServletRequest req, final SlingHttpServletResponse resp)
            throws IOException {
        PrintWriter out = resp.getWriter();
        pomGen.generatePOM(bdlCxt, out, "", false);
    }

    /**
     * Activate , initialize prefix.
     * 
     * @param context
     *            The component context.
     */
    protected final void activate(final ComponentContext context) {
        bdlCxt = context.getBundleContext();
    }
}
