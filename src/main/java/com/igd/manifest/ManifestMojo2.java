package com.igd.manifest;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
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

//@Mojo(name = "build", defaultPhase = LifecyclePhase.PACKAGE)
public class ManifestMojo2 extends AbstractMojo {

	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	private MavenProject project;

	@Parameter(property = "manifest.sourceDir", required = true, defaultValue = "${project.build.directory}")
	private File sourceDir;

	@Parameter(property = "manifest.sourceJar", required = true, defaultValue = "${project.build.finalName}.jar")
	private String sourceJar;

	@Parameter(property = "manifest.targetDir", required = true, defaultValue = "${project.build.directory}")
	private File targetDir;

	@Parameter(property = "manifest.targetJar", required = true, defaultValue = "${project.build.finalName}.xjar")
	private String targetJar;
	
	@Parameter(property = "manifest.mainClass", required = false)
	private String mainClass;
	
	@Parameter(property = "manifest.manifests", required = false)
	private Map<String, String> manifests;

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

		File src = new File(this.sourceDir, this.sourceJar);
		File dest = new File(this.targetDir, this.targetJar);
//		
//		JarArchiveInputStream zis = null;
//	    JarArchiveOutputStream zos = null;
//		try(FileInputStream fis = new FileInputStream(src); 
//		        FileOutputStream fos = new FileOutputStream(dest)) {
//			
//		
//		}
		try(JarInputStream ins = new JarInputStream(new FileInputStream(src)); 
				JarOutputStream jos = new JarOutputStream(new FileOutputStream(dest))) {
			Manifest manifest = new Manifest(ins);
			Attributes att = manifest.getMainAttributes();
			att.replace("Main-Class", mainClass);
			manifest.write(jos);
			//String path = this.sourceDir + "/" + this.sourceJar;
//			JarFile jarFile = new JarFile(new File(this.sourceDir , this.sourceJar));
//			copyJarByJarFile(jarFile, jos);
//			Manifest manifest = jarFile.getManifest();
//			Attributes att = manifest.getMainAttributes();
//			att.forEach((k,v)-> {
//				System.out.println(k + "=" +v);
//			});
//			att.replace("Main-Class", mainClass);
//			//manifest.write(jos);
			jos.flush();
			jos.close();
//			Enumeration<JarEntry> entries = jarFile.entries();
//			while (entries.hasMoreElements()) {
//				JarEntry jarEntry = entries.nextElement();
//				jarEntry.get
//				if("META-INF/MANIFEST.MF".equals(jarEntry.getName())) {
//					Manifest manifest = new Manifest(jarFile.getInputStream(jarEntry));
//			        Attributes attributes = manifest.getMainAttributes();
//			        String str = attributes.getValue("Main-Class");
//			          if (str != null) {
//			            attributes.putValue("Jar-Main-Class", str);
//			            attributes.putValue("Main-Class", "io.xjar.jar.XJarLauncher");
//			          } 
////			          JarArchiveEntry jarArchiveEntry = new JarArchiveEntry(entry.getName());
////			          jarArchiveEntry.setTime(entry.getTime());
////			          zos.putArchiveEntry((ArchiveEntry)jarArchiveEntry);
//			          //manifest.write((OutputStream)nos);
//				}
//	            if (!jarEntry.isDirectory()) {
//	                System.out.println("jar:file:/" + path + "!/" + jarEntry.getName());
//	            }
//	        }
		} catch(Exception e) {
			
		}
	}
	
	private void copyJarByJarFile(JarFile srcJarFile, JarOutputStream jos) throws IOException{
        Enumeration<JarEntry> jarEntrys = srcJarFile.entries();
        byte[] bytes = new byte[1024];
        
        while(jarEntrys.hasMoreElements()){
            JarEntry entryTemp = jarEntrys.nextElement();
            jos.putNextEntry(entryTemp);
            BufferedInputStream in = new BufferedInputStream(srcJarFile.getInputStream(entryTemp));
            int len = in.read(bytes, 0, bytes.length);
            while(len != -1){
            	jos.write(bytes, 0, len);
                len = in.read(bytes, 0, bytes.length);
            }
            in.close();
        }
	}

}
