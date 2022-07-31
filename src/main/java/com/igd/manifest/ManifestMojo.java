package com.igd.manifest;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Enumeration;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

@Mojo(name = "build", defaultPhase = LifecyclePhase.PACKAGE)
public class ManifestMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	private MavenProject project;

	@Parameter(property = "manifest.sourceDir", required = true, defaultValue = "${project.build.directory}")
	private File sourceDir;

	@Parameter(property = "manifest.sourceJar", required = true, defaultValue = "${project.build.finalName}.jar")
	private String sourceJar;

	@Parameter(property = "manifest.targetDir", required = true, defaultValue = "${project.build.directory}")
	private File targetDir;

	@Parameter(property = "manifest.targetJar", required = true, defaultValue = "${project.build.finalName}.jar")
	private String targetJar;
	
	@Parameter(property = "manifest.mainClass", required = false)
	private String mainClass;
	
	@Parameter(property = "manifest.map", required = false)
	private Map<String, String> map;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		Build build = this.project.getBuild();
		Map<String, Plugin> plugins = build.getPluginsAsMap();
		Plugin plugin = plugins.get("org.springframework.boot:spring-boot-maven-plugin");
		if (plugin != null) {
			Object configuration = plugin.getConfiguration();
			if (configuration instanceof Xpp3Dom) {
				Xpp3Dom dom = (Xpp3Dom) configuration;
				Xpp3Dom child = dom.getChild("executable");
				String executable = (child != null) ? child.getValue() : null;
				if ("true".equalsIgnoreCase(executable)) {
					String msg = "Unsupported to build for an <executable>true</executable> spring boot JAR file, ";
					msg = msg
							+ "maybe you should upgrade manifest-maven-plugin dependency if it have been supported in the later versions,";
					msg = msg
							+ "if not, delete <executable>true</executable> or set executable as false for the configuration of spring-boot-maven-plugin.";
					throw new MojoFailureException(msg);
				}
				child = dom.getChild("embeddedLaunchScript");
				String embeddedLaunchScript = (child != null) ? child.getValue() : null;
				if (embeddedLaunchScript != null) {
					String msg = "Unsupported to build for an <embeddedLaunchScript>...</embeddedLaunchScript> spring boot JAR file, ";
					msg = msg
							+ "maybe you should upgrade manifest-maven-plugin dependency if it have been supported in the later versions,";
					msg = msg
							+ "if not, delete <embeddedLaunchScript>...</embeddedLaunchScript> for the configuration of spring-boot-maven-plugin.";
					throw new MojoFailureException(msg);
				}
			}
		}

		try {
			JarFile jarFile = new JarFile(new File(this.sourceDir , this.sourceJar));
			
			ByteArrayOutputStream newJarOut = new ByteArrayOutputStream();
			JarOutputStream jos = new JarOutputStream(newJarOut);
			
			Enumeration<JarEntry> entries = jarFile.entries();
			byte[] bytes = new byte[1024];
			while (entries.hasMoreElements()) {
				JarEntry jarEntry = entries.nextElement();
				String vmainClass = null;
				if(JarFile.MANIFEST_NAME.equals(jarEntry.getName())) {
					Manifest manifest = jarFile.getManifest();
					Attributes att = manifest.getMainAttributes();
					if(map != null && map.size() > 0) {
						map.entrySet().forEach((v) -> {
							if(!Attributes.Name.MAIN_CLASS.toString().equals(v.getKey())) {
								att.put(new Name(v.getKey()), v.getValue());
							}
						});
						vmainClass = map.get(Attributes.Name.MAIN_CLASS.toString());
					}
					
					if(mainClass == null || mainClass.length() == 0) {
						mainClass = vmainClass;
					}
					
					if(mainClass != null && mainClass.length() > 0) {
						att.put(Attributes.Name.MAIN_CLASS, mainClass);	
					}
					ByteArrayOutputStream manifestOut = new ByteArrayOutputStream();
					manifest.write(manifestOut);
					jos.putNextEntry(new JarEntry(JarFile.MANIFEST_NAME));
					jos.write(manifestOut.toByteArray());
				} else {
					jos.putNextEntry(new JarEntry(jarEntry.getName()));
					BufferedInputStream in = new BufferedInputStream(jarFile.getInputStream(jarEntry));
		            int len = in.read(bytes, 0, bytes.length);
		            while(len != -1){
		            	jos.write(bytes, 0, len);
		                len = in.read(bytes, 0, bytes.length);
		            }
		            in.close();	
				}
			}
			
			File dest = new File(this.targetDir, this.targetJar);
			FileOutputStream fous = new FileOutputStream(dest);
			jarFile.close();
			jos.flush();
			jos.close();
			
			fous.write(newJarOut.toByteArray());
			
			fous.close();
		} catch(Exception e) {
			
		}
	}

}
