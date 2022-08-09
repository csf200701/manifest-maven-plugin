package com.igd.manifest;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
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
	
	
	private String METAINF = "META-INF";

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
		
		try (JarFile jarFile = new JarFile(new File(this.sourceDir , this.sourceJar))) {
			ByteArrayOutputStream newJarOut = new ByteArrayOutputStream();
			JarOutputStream jos = new JarOutputStream(newJarOut);
			String manifestMainClass = mainClass;
			
			//
			Map<String, byte[]> resolveMap = new HashMap<>();
			Enumeration<JarEntry> entries = jarFile.entries();
			while (entries.hasMoreElements()) {
				JarEntry jarEntry = entries.nextElement();
				if(jarEntry.getName().equals(JarFile.MANIFEST_NAME)) {
					continue;
				}
				
				JarEntry newJarEntry = new JarEntry(jarEntry.getName());
				newJarEntry.setMethod(jarEntry.getMethod());
            	newJarEntry.setSize(jarEntry.getSize());
            	newJarEntry.setCrc(jarEntry.getCrc());
            	newJarEntry.setLastModifiedTime(jarEntry.getLastModifiedTime());
				
				if(jarEntry.isDirectory()) {
					jos.putNextEntry(new JarEntry(newJarEntry));
					continue;
				}
				
				BufferedInputStream in = new BufferedInputStream(jarFile.getInputStream(jarEntry));
				byte[] barr = readJarEntryByteArray(in, true);
            	//newJarEntry.setCreationTime(jarEntry.getCreationTime());
            	//newJarEntry.setLastAccessTime(jarEntry.getLastAccessTime());
            	jos.putNextEntry(new JarEntry(newJarEntry));
	            jos.write(barr);
	            
	            if(jarEntry.getName().endsWith(".jar")) {
	            	String prefixKey = jarEntry.getName() + "!/";
	            	JarInputStream jis = new JarInputStream(new ByteArrayInputStream(barr));
	            	JarEntry childJarEntry = null;
					while((childJarEntry = jis.getNextJarEntry()) != null) {
						barr = readJarEntryByteArray(jis ,false);
						resolveMap.put(prefixKey + childJarEntry.getName(), barr);	
					}
					jis.close();
	            } else {
	            	resolveMap.put(jarEntry.getName(), barr);	
	            }
			}
			
			// 设置Manifest
			Manifest manifest = jarFile.getManifest();
			Attributes att = manifest.getMainAttributes();
			Map<String, String> manifestMap = Optional.ofNullable(map).orElse(Collections.emptyMap());
			if(manifestMainClass == null || manifestMainClass.length() == 0) {
				manifestMainClass = manifestMap.remove(Attributes.Name.MAIN_CLASS.toString());
			}
			if(manifestMainClass != null && manifestMainClass.length() > 0) {
				String oldMainClass = (String) att.put(Attributes.Name.MAIN_CLASS, manifestMainClass);	
				if(oldMainClass != null && !oldMainClass.equals(mainClass)) {
					att.put(new Name("Pre-Jar-Main-Class"), oldMainClass);
				}
			}
			manifestMap.entrySet().forEach((v) -> {
				att.put(new Name(v.getKey()), v.getValue());
			});
			ByteArrayOutputStream manifestOut = new ByteArrayOutputStream();
			manifest.write(manifestOut);
			jos.putNextEntry(new JarEntry(JarFile.MANIFEST_NAME));
			jos.write(manifestOut.toByteArray());
			
			//
			if(manifestMainClass != null && manifestMainClass.length() > 0) {
				String mainclassStr = manifestMainClass.replaceAll("\\.", "/") + ".class";
				if(resolveMap.containsKey(mainclassStr)) {
					return;
				}
				
				Set<Entry<String, byte[]>> set = resolveMap.entrySet();
				
				String fullPackagePrefix = null;
				for(Entry<String, byte[]> entry : set) {
					if(!entry.getKey().endsWith(mainclassStr)) {
						continue;
					}
					String prefix = entry.getKey().substring(0, entry.getKey().lastIndexOf(mainclassStr));
					if(entry.getKey().startsWith(prefix)) {
						fullPackagePrefix = prefix;
						break;
					}
				}
				
				if(fullPackagePrefix != null && !"".equals(fullPackagePrefix)) {
					for(Entry<String, byte[]> entry : set) {
						if(!entry.getKey().equals(fullPackagePrefix) && 
								   entry.getKey().indexOf(fullPackagePrefix) == 0) {
							String packageStr = entry.getKey().substring(entry.getKey().indexOf(fullPackagePrefix)+fullPackagePrefix.length());
							if(packageStr.startsWith(METAINF)) {
								continue;
							}
							jos.putNextEntry(new JarEntry(packageStr));
							jos.write(entry.getValue());	
						}
					}	
				} 
			}
			
			
			
			File dest = new File(this.targetDir, this.targetJar);
			FileOutputStream fous = new FileOutputStream(dest);
			jarFile.close();
			jos.flush();
			jos.close();
			
			fous.write(newJarOut.toByteArray());
			fous.flush();
			fous.close();
		} catch (Exception e) {
			e.printStackTrace();
			throw new MojoExecutionException("Mainifest gen fail", e);
		}
	}
	
	private byte[] readJarEntryByteArray(InputStream in , boolean isClose) throws IOException {
		byte[] bytes = new byte[1024];
		ByteArrayOutputStream dataOut = new ByteArrayOutputStream();
        int len = in.read(bytes, 0, bytes.length);
        while(len != -1){
        	dataOut.write(bytes, 0, len);
            len = in.read(bytes, 0, bytes.length);
        }
        byte[] barr = dataOut.toByteArray();
        if(isClose) {
        	in.close();		
        }
        dataOut.close();
		return barr;
	}
	
}
