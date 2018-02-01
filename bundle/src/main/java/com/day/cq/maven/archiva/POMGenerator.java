package com.day.cq.maven.archiva;

import java.io.IOException;
import java.io.PrintWriter;

import org.osgi.framework.BundleContext;

public interface POMGenerator {

	void generatePOM(BundleContext bctxt, PrintWriter out, String version, boolean includeVersionScope) throws IOException;

}
