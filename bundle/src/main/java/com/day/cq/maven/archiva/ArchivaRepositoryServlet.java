package com.day.cq.maven.archiva;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author tyge
 */
@Service(value=javax.servlet.Servlet.class)
@Component(immediate=true,metatype=true)
@Properties({
	@Property(name="sling.servlet.methods",value="GET",propertyPrivate=true),
	@Property(name="service.description",value="Archiva Repository Servlet",propertyPrivate=true),
	@Property(name="archiva.groupid",value="com.day",propertyPrivate=false),
	@Property(name="archiva.artifactid",value="cq5-parent",propertyPrivate=false)
})
@SuppressWarnings("serial")
public class ArchivaRepositoryServlet extends HttpServlet {
    /**
     * The logger used in this class.
     */
    private static final Logger LOGGER = LoggerFactory
            .getLogger(ArchivaRepositoryServlet.class);

    private final static String ARCHIVA_SERVICE = "archiva-service";

    @Reference
    private SlingRepository repository;

    @Reference(policy=ReferencePolicy.STATIC)
    private ResourceResolverFactory resourceResolverFactory;
    
    /** The resource resolver. */
    ResourceResolver resourceResolver = null;

    @Reference(policy=ReferencePolicy.STATIC)
    private MessageFormatProvider mfp;

    @Reference(policy=ReferencePolicy.STATIC)
    private POMGenerator pomGen;

    @Reference
    private HttpService httpService;

    private boolean servletRegistered = false;

	@Property(name="sling.servlet.paths",propertyPrivate=true)
    public static final String mavenRepositoryPath = "/maven/repository";

    private BundleContext bdlCxt = null;

    private String archivaGroupId = null;

    private String archivaArtifactId = null;

    private MessageFormat metadataMessage = null;

    private MessageFormat pomfileMessage = null;

    /**
     * inherited.
     * 
     * @see org.apache.sling.api.servlets.SlingSafeMethodsServlet#doGet(org.apache.sling.api.SlingHttpServletRequest,
     *      org.apache.sling.api.SlingHttpServletResponse) {@inheritDoc}
     */
    protected final void doGet(final HttpServletRequest request,
            final HttpServletResponse response) throws IOException {
        boolean isSHA1 = request.getRequestURI().endsWith(".sha1");
        OutputStream output = response.getOutputStream();
        if (isSHA1) {
            try {
                MessageDigest hash = MessageDigest.getInstance("SHA1");
                hash.reset();
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                output = new DigestOutputStream(byteArrayOutputStream, hash);
            } catch (NoSuchAlgorithmException e) {
                System.out.println("No SHA1 available");
            }
        }

        doProcessRequest(request, response, output);
        if (isSHA1) {
            DigestOutputStream digestOutput = (DigestOutputStream) output;
            byte[] digest = digestOutput.getMessageDigest().digest();
            String hexStr = "";
            for (int i = 0; i < digest.length; i++) {
                hexStr += Integer.toString((digest[i] & 0xff) + 0x100, 16)
                        .substring(1);
            }
            response.getOutputStream().write(hexStr.getBytes());
        }
    }

    /**
     * inherited.
     * 
     * @see org.apache.sling.api.servlets.SlingSafeMethodsServlet#doPost(org.apache.sling.api.SlingHttpServletRequest,
     *      org.apache.sling.api.SlingHttpServletResponse) {@inheritDoc}
     */
    protected final void doPut(final HttpServletRequest request,
            final HttpServletResponse response) throws IOException {

        String uri = request.getRequestURI().substring(
                mavenRepositoryPath.length());
        if (uri.endsWith(".pom") || uri.endsWith(".jar")) {
            try {
                Session session = getArchivaServiceResolver().adaptTo(Session.class);
                String nodePath = "var/maven/repository" + uri;
                Node fileContentNode = getOrCreateNode(session, nodePath);
                StringWriter writer = new StringWriter();
                IOUtils.copy(request.getInputStream(), writer, "UTF-8");
                fileContentNode.setProperty("jcr:data", writer.toString());
                session.save();
                session.logout();
            } catch (RepositoryException e) {
                LOGGER.error(e.getMessage());
            }
        }
    }

    private Node getOrCreateNode(final Session session, final String path)
            throws RepositoryException {
        Node newNode = null;
        String[] labels = path.split("/");
        Node startNode = session.getRootNode();
        int i = 0;
        String label = null;
        for (; i < labels.length - 1; i++) {
            label = labels[i];
            if (startNode.hasNode(label)) {
                startNode = startNode.getNode(label);
            } else {
                startNode = startNode.addNode(label, "nt:folder");
            }
        }
        label = labels[i];
        if (startNode.hasNode(label)) {
            newNode = startNode.getNode(label).getNode("jcr:content");
        } else {
            newNode = startNode.addNode(label, "nt:file").addNode(
                    "jcr:content", "nt:resource");
            newNode.setProperty("jcr:lastModified", Calendar.getInstance());
            newNode.setProperty("jcr:mimeType", "application/octet-stream");
        }

        return newNode;
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
    private void doProcessRequest(final HttpServletRequest req,
            final HttpServletResponse resp, final OutputStream output)
            throws IOException {

        String uri = req.getRequestURI()
                .substring(mavenRepositoryPath.length());
        String[] slashy = uri.split("/");
        // PrintWriter out = response.getWriter();
        LOGGER.warn("URIs = " + uri);
        String lookupGroupId = "";
        if (slashy.length < 3) {
            // request doesn't contain valid address
            // write simple response
            String path = req.getRequestURI();
            PrintWriter printWriter = new PrintWriter(output);
            printWriter.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\">");
            printWriter.append("<html><head><title>Index of " + path + "</title></head><body><h1>Index of " + path + "</h1>");
            printWriter.append("<pre><hr></pre></body></html>");
            printWriter.flush();
            return;
        }
        for (int i = 1; i < slashy.length - 3; i++) {
            if (lookupGroupId.length() > 0)
                lookupGroupId += ".";
            lookupGroupId += slashy[i];
        }
        String lookupArtifactId = slashy[slashy.length - 3];
        String lookupVersion = slashy[slashy.length - 2];
        String filename = slashy[slashy.length - 1];
        System.out.println("filename = " + filename);
        if (filename.endsWith(".sha1")) {
            filename = filename.substring(0, filename.length() - 5);
        }
        boolean found = false;
        if (lookupGroupId.equals(archivaGroupId)
                && lookupArtifactId.equals(archivaArtifactId)) {
            found = generateVirtualParentPOM(output, lookupGroupId,
                    lookupArtifactId, lookupVersion, filename);
        } else {
            found = findInBundles(resp, lookupGroupId, lookupArtifactId,
                    lookupVersion, filename, output, found);
        }
        if (!found) {
            resp.setStatus(404);
        }
    }

    /**
     * @param output
     * @param lookupGroupId
     * @param lookupArtifactId
     * @param lookupVersion
     * @param filename
     * @return
     * @throws IOException
     */
    private boolean generateVirtualParentPOM(final OutputStream output,
            String lookupGroupId, String lookupArtifactId,
            String lookupVersion, String filename) throws IOException {
        boolean found;
        if (filename.startsWith("maven-metadata.xml")) {
            System.out.println("Generating metadata xml");
            output.write(getMetadataMessage().format(
                    new Object[] { lookupGroupId, lookupArtifactId,
                            lookupVersion }).getBytes());
        } else if (filename.endsWith(".pom")) {
            System.out.println("Generating pom xml");
            pomGen.generatePOM(bdlCxt, new PrintWriter(
                    output), lookupVersion, true);
        }
        found = true;
        return found;
    }

    /**
     * @param resp
     * @param lookupGroupId
     * @param lookupArtifactId
     * @param lookupVersion
     * @param filename
     * @param found
     * @return
     * @throws IOException
     */
    private boolean findInBundles(final HttpServletResponse resp,
            final String lookupGroupId, final String lookupArtifactId,
            final String lookupVersion, final String filename,
            final OutputStream output, boolean found) throws IOException {
        if (found)
            return found;
        for (Bundle bundle : bdlCxt.getBundles()) {
            Enumeration<?> enumResources = bundle.findEntries("META-INF",
                    "pom.properties", true);
            if (enumResources != null) {
                while (enumResources.hasMoreElements() && !found) {
                    URL resource = (URL) enumResources.nextElement();
                    java.util.Properties props = new java.util.Properties();
                    props.load(resource.openStream());
                    String groupId = props.get("groupId").toString();
                    String artifactId = props.get("artifactId").toString();
                    String version = props.get("version").toString();
                    if (lookupArtifactId.equals(artifactId)
                            && lookupGroupId.equals(groupId)
                            && lookupVersion.equals(version)) {
                        if (filename.startsWith("maven-metadata.xml")) {
                            System.out.println("Generating metadata xml");
                            output.write(getMetadataMessage()
                                    .format(
                                            new Object[] { groupId, artifactId,
                                                    version }).getBytes());
                        } else if (filename.endsWith(".pom")) {
                            System.out.println("Generating pom xml");
                            output.write(getPOMFileMessage()
                                    .format(
                                            new Object[] { groupId, artifactId,
                                                    version }).getBytes());
                        } else {
                            System.out.println("output as zip file");
                            writeZip(output, bundle);
                        }
                        found = true;
                    }
                }
            }
            if (found) {
                break;
            }
        }
        return found;
    }


    /**
     * @param response
     * @param bundle
     * @throws IOException
     */
    private void writeZip(OutputStream output, Bundle bundle)
            throws IOException {
        ArrayList<String> inZip = new ArrayList<String>();
        Enumeration<?> allResources = bundle.findEntries("/", null, true);
        if (allResources != null) {
            ZipOutputStream zipOutputStream = new ZipOutputStream(output);
            while (allResources.hasMoreElements()) {
                URL uriResourceToZip = (URL) allResources.nextElement();
                String path = uriResourceToZip.getPath();
                System.out.println(path);
                if (inZip.contains(path.substring(1))) {
                    continue;
                }
                ZipEntry entry = new ZipEntry(path.substring(1));
                entry.setTime(bundle.getLastModified());
                zipOutputStream.putNextEntry(entry);
                InputStream input = uriResourceToZip.openStream();
                IOUtils.copy(input, zipOutputStream);
                zipOutputStream.closeEntry();
                input.close();
                inZip.add(path.substring(1));
            }
            zipOutputStream.close();
        } else {
            throw new IOException("No resource found");
        }
    }

    /**
     * Activate , initialize prefix.
     * 
     * @param context
     *            The component context.
     */
    protected final void activate(final ComponentContext context) {
        bdlCxt = context.getBundleContext();

        try {
            httpService.registerServlet(mavenRepositoryPath, this, null, null);
            servletRegistered = true;
            Dictionary<?, ?> dict = context.getProperties();
            archivaGroupId = dict.get("archiva.groupid").toString();
            archivaArtifactId = dict.get("archiva.artifactid").toString();
        } catch (ServletException e) {
            LOGGER.error(e.getMessage());
        } catch (NamespaceException e) {
            LOGGER.error(e.getMessage());
        }
    }

    protected void deactivate(ComponentContext context) {

        if (servletRegistered) {
            httpService.unregister(mavenRepositoryPath);
            servletRegistered = false;
        }

        bdlCxt = null;
    }

    private MessageFormat getMetadataMessage() {
        if (metadataMessage == null) {
            metadataMessage = mfp.getMessage("maven-metadata.xml");
        }
        return metadataMessage;
    }

    private MessageFormat getPOMFileMessage() {
        if (pomfileMessage == null) {
            pomfileMessage = mfp.getMessage("virtual_pom.xml");
        }
        return pomfileMessage;
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

