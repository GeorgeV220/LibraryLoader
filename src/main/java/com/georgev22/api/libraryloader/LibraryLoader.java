/*
 * This file is part of helper, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package com.georgev22.api.libraryloader;

import com.georgev22.api.libraryloader.annotations.MavenLibrary;
import com.georgev22.api.libraryloader.exceptions.InvalidDependencyException;
import com.georgev22.api.libraryloader.exceptions.UnknownDependencyException;
import me.lucko.jarrelocator.JarRelocator;
import me.lucko.jarrelocator.Relocation;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnmodifiableView;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.regex.Pattern;


/**
 * The LibraryLoader class is responsible for dynamically loading and unloading libraries or dependencies at runtime.
 * It provides methods to load dependencies from Maven repositories or local paths, as well as unload them.
 * The class uses a specified class loader to load the libraries and keeps track of the loaded dependencies.
 */
public final class LibraryLoader {

    /**
     * Access to the class loader for loading and unloading libraries.
     */
    private final ClassLoaderAccess classLoaderAccess;

    /**
     * Logger for logging messages during library loading and unloading.
     */
    private final Logger logger;

    /**
     * The folder where the libraries are stored.
     */
    private final File dataFolder;

    /**
     * List of loaded dependencies.
     */
    private final List<Dependency> dependencyList = new ArrayList<>();

    /**
     * Constructs a LibraryLoader instance with the specified class, class loader, data folder, and logger.
     *
     * @param classLoader the class loader to use for loading libraries
     * @param dataFolder  the folder where the libraries are stored
     * @param logger      the logger for logging messages
     * @param <T>         the type of the class
     */
    public <T> LibraryLoader(@NotNull URLClassLoader classLoader,
                             @NotNull File dataFolder, @NotNull Logger logger) {
        this.classLoaderAccess = new ClassLoaderAccess(classLoader);
        this.classLoaderAccess.registerLogger(logger);
        this.logger = logger;
        this.dataFolder = dataFolder;
    }

    /**
     * Constructs a LibraryLoader instance with the specified class, class loader, data folder, and logger.
     *
     * @param classLoader the class loader to use for loading libraries
     * @param dataFolder  the folder where the libraries are stored
     * @param logger      the logger for logging messages
     * @param <T>         the type of the class
     */
    public <T> LibraryLoader(@NotNull ClassLoader classLoader,
                             @NotNull File dataFolder, @NotNull Logger logger) {
        this.classLoaderAccess = new ClassLoaderAccess(classLoader);
        this.classLoaderAccess.registerLogger(logger);
        this.logger = logger;
        this.dataFolder = dataFolder;
    }

    /**
     * Constructs a LibraryLoader instance with the specified class, class loader, and data folder.
     * The logger is set to the default logger for the class.
     *
     * @param classLoader the class loader to use for loading libraries
     * @param dataFolder  the folder where the libraries are stored
     * @param <T>         the type of the class
     */
    public <T> LibraryLoader(@NotNull URLClassLoader classLoader, @NotNull File dataFolder) {
        this.classLoaderAccess = new ClassLoaderAccess(classLoader);
        this.logger = Logger.getLogger(this.getClass().getSimpleName());
        this.classLoaderAccess.registerLogger(this.logger);
        this.dataFolder = dataFolder;
    }

    /**
     * Constructs a LibraryLoader instance with the specified class, class loader, and data folder.
     * The logger is set to the default logger for the class.
     *
     * @param classLoader the class loader to use for loading libraries
     * @param dataFolder  the folder where the libraries are stored
     * @param <T>         the type of the class
     */
    public <T> LibraryLoader(@NotNull ClassLoader classLoader, @NotNull File dataFolder) {
        this.classLoaderAccess = new ClassLoaderAccess(classLoader);
        this.logger = Logger.getLogger(this.getClass().getSimpleName());
        this.classLoaderAccess.registerLogger(this.logger);
        this.dataFolder = dataFolder;
    }

    /**
     * Constructs a LibraryLoader instance with the specified class and data folder.
     * The class loader is set to the default class loader for the class.
     * The logger is set to the default logger for the class.
     *
     * @param dataFolder the folder where the libraries are stored
     * @param <T>        the type of the class
     */
    public <T> LibraryLoader(@NotNull File dataFolder) {
        this.classLoaderAccess = new ClassLoaderAccess(this.getClass().getClassLoader());
        this.logger = Logger.getLogger(this.getClass().getSimpleName());
        this.classLoaderAccess.registerLogger(this.logger);
        this.dataFolder = dataFolder;
    }

    /**
     * Loads all dependencies specified by MavenLibrary annotations in the object's class.
     *
     * @param object    the object whose class's dependencies should be loaded
     * @param pathCheck flag indicating whether to check if the dependency is already in the class path
     * @throws InvalidDependencyException if the dependency is already loaded or in the class path
     * @throws UnknownDependencyException if the dependency cannot be downloaded or loaded
     */
    public void loadAll(@NotNull Object object, boolean pathCheck) throws InvalidDependencyException, UnknownDependencyException {
        loadAll(object.getClass(), pathCheck);
    }

    /**
     * Loads all dependencies specified by MavenLibrary annotations in the specified class.
     *
     * @param clazz     the class whose dependencies should be loaded
     * @param pathCheck flag indicating whether to check if the dependency is already in the class path
     * @param <T>       the type of the class
     * @throws InvalidDependencyException if the dependency is already loaded or in the class path
     * @throws UnknownDependencyException if the dependency cannot be downloaded or loaded
     */
    public <T> void loadAll(@NotNull Class<T> clazz, boolean pathCheck) throws InvalidDependencyException, UnknownDependencyException {
        MavenLibrary[] libs = clazz.getDeclaredAnnotationsByType(MavenLibrary.class);

        for (MavenLibrary lib : libs) {
            if (
                    !lib.groupId().equalsIgnoreCase("") ||
                            !lib.artifactId().equalsIgnoreCase("") ||
                            !lib.version().equalsIgnoreCase("")
            )
                load(lib.groupId(), lib.artifactId(), lib.version(), lib.repo().value(), pathCheck);
            else {
                String[] dependency = lib.value().split(":", 4);
                load(dependency[0], dependency[1], dependency[2], dependency[3], pathCheck);
            }
        }
    }

    /**
     * Loads a dependency with the specified group ID, artifact ID, version, and repository URL.
     *
     * @param groupId    the group ID of the dependency
     * @param artifactId the artifact ID of the dependency
     * @param version    the version of the dependency
     * @param repoUrl    the URL of the repository where the dependency is hosted
     * @param pathCheck  flag indicating whether to check if the dependency is already in the class path
     * @throws InvalidDependencyException if the dependency is already loaded or in the class path
     * @throws UnknownDependencyException if the dependency cannot be downloaded or loaded
     */
    public void load(String groupId, String artifactId, String version, String repoUrl, boolean pathCheck) throws InvalidDependencyException, UnknownDependencyException {
        load(new Dependency(groupId, artifactId, version, repoUrl), pathCheck);
    }

    /**
     * Loads a list of dependencies with the specified path check.
     *
     * @param dependencies the list of dependencies to load
     * @param pathCheck    flag indicating whether to check if the dependencies are already in the class path
     * @throws InvalidDependencyException if a dependency is already loaded or in the class path
     * @throws UnknownDependencyException if a dependency cannot be downloaded or loaded
     */
    public void load(@NotNull List<Dependency> dependencies, boolean pathCheck) throws InvalidDependencyException, UnknownDependencyException {
        for (Dependency d : dependencies) {
            load(d, pathCheck);
        }
    }

    /**
     * Loads a dependency specified by the Dependency object.
     *
     * @param d         the dependency to load
     * @param pathCheck flag indicating whether to check if the dependency is already in the class path
     * @throws InvalidDependencyException if the dependency is already loaded or in the class path
     * @throws UnknownDependencyException if the dependency cannot be downloaded or loaded
     */
    public void load(@NotNull Dependency d, boolean pathCheck) throws InvalidDependencyException, UnknownDependencyException {
        if (dependencyList.contains(d)) {
            logger.warning(String.format("Dependency %s:%s:%s is already loaded!", d.groupId, d.artifactId, d.version));
            return;
        }

        logger.info(String.format("Loading dependency %s:%s:%s from %s", d.groupId, d.artifactId, d.version, d.repoUrl));

        String name = d.artifactId + "-" + d.version;

        File saveLocationDir = new File(getLibFolder(), d.groupId.replace(".", File.separator) + File.separator + d.artifactId.replace(".", File.separator) + File.separator + d.version);

        if (!saveLocationDir.exists()) {
            logger.info(String.format("Creating directory for dependency %s:%s:%s from %s", d.groupId, d.artifactId, d.version, d.repoUrl));
            if (saveLocationDir.mkdirs()) {
                logger.info(String.format("The directory for dependency %s:%s:%s was successfully created!!", d.groupId, d.artifactId, d.version));
            }
        }

        File saveLocation = new File(saveLocationDir, name + ".jar");
        if (!saveLocation.exists()) {

            try {
                logger.info("Dependency '" + name + "' does not exist in the libraries folder. Attempting to download...");
                URL url = d.url();

                RelocatedDependency relocatedDependency = d instanceof RelocatedDependency ? (RelocatedDependency) d : null;

                try (InputStream is = url.openStream()) {
                    if (relocatedDependency != null) {
                        Path tempFilePath = Files.createTempFile(relocatedDependency.artifactId + "-" + relocatedDependency.version, ".tmp");

                        Files.copy(is, tempFilePath, StandardCopyOption.REPLACE_EXISTING);

                        JarRelocator relocator = new JarRelocator(tempFilePath.toFile(), saveLocation, relocatedDependency.getRelocations());

                        try {
                            relocator.run();
                        } catch (IOException e) {
                            throw new UnknownDependencyException(e, "Unable to relocate" + d + "' dependency.");
                        }
                    } else {
                        Files.copy(is, saveLocation.toPath());
                    }
                }

            } catch (IOException e) {
                throw new UnknownDependencyException(e, "Unable to download '" + d + "' dependency.");
            }

            logger.info("Dependency '" + name + "' successfully downloaded.");
        }

        if (!saveLocation.exists()) {
            throw new UnknownDependencyException("Unable to download '" + d + "' dependency.");
        }

        try {
            if (pathCheck & (this.classLoaderAccess.contains(saveLocation.toURI().toURL()) | this.classLoaderAccess.contains(d))) {
                throw new InvalidDependencyException("Dependency " + d + " is already in the class path.");
            }
            this.classLoaderAccess.add(saveLocation.toURI().toURL());
        } catch (Exception e) {
            throw new InvalidDependencyException("Unable to load '" + saveLocation + "' dependency.", e);
        }

        logger.info("Loaded dependency '" + name + "' successfully.");
        dependencyList.add(d);
    }

    /**
     * Unloads all loaded dependencies.
     *
     * @throws InvalidDependencyException if the dependency is not loaded or cannot be unloaded
     */
    public void unloadAll() throws InvalidDependencyException {
        List<Dependency> dependencies = new ArrayList<>(dependencyList);
        for (Dependency d : dependencies) {
            unload(d);
        }
    }

    /**
     * Unloads a specific dependency.
     *
     * @param d the dependency to unload
     * @throws InvalidDependencyException if the dependency is not loaded or cannot be unloaded
     */
    public void unload(Dependency d) throws InvalidDependencyException {
        if (!dependencyList.contains(d)) {
            logger.warning(String.format("Dependency %s:%s:%s is not loaded!", d.groupId, d.artifactId, d.version));
            return;
        }

        logger.info(String.format("Unloading dependency %s:%s:%s", d.groupId, d.artifactId, d.version));


        String name = d.artifactId + "-" + d.version;

        File saveLocationDir = new File(getLibFolder(), d.groupId.replace(".", File.separator) + File.separator + d.artifactId.replace(".", File.separator) + File.separator + d.version);

        if (!saveLocationDir.exists()) {
            throw new InvalidDependencyException(String.format("The directory for dependency %s:%s:%s does not exists!!", d.groupId, d.artifactId, d.version));
        }

        File saveLocation = new File(saveLocationDir, name + ".jar");
        if (!saveLocation.exists()) {
            throw new InvalidDependencyException("Unable to unload '" + d + "' dependency.");
        }

        try {
            this.classLoaderAccess.remove(saveLocation.toURI().toURL());
        } catch (Exception e) {
            throw new InvalidDependencyException("Unable to unload dependency " + d, e);
        }

        logger.info(String.format("Unloaded dependency %s:%s:%s successfully", d.groupId, d.artifactId, d.version));
        dependencyList.remove(d);
    }

    /**
     * Returns the folder where the libraries are stored.
     *
     * @return the library folder
     */
    public @NotNull File getLibFolder() {
        File libs = new File(dataFolder, "libraries");
        if (libs.mkdirs()) {
            logger.info("libraries folder created!");
        }
        return libs;
    }

    /**
     * Returns the list of loaded dependencies.
     *
     * @return the list of loaded dependencies
     */
    @Contract(pure = true)
    public @NotNull @UnmodifiableView List<Dependency> getDependencyList() {
        return Collections.unmodifiableList(dependencyList);
    }

    /**
     * Represents a dependency with the specified group ID, artifact ID, version, and repository URL.
     */
    @NotNull
    public static class Dependency {

        public final String groupId;
        public final String artifactId;
        public final String version;
        public final String repoUrl;

        /**
         * Constructs a new Dependency with the given group ID, artifact ID, version, and repository URL.
         *
         * @param groupId    the group ID of the dependency
         * @param artifactId the artifact ID of the dependency
         * @param version    the version of the dependency
         * @param repoUrl    the URL of the repository where the dependency is hosted
         */
        public Dependency(String groupId, String artifactId, String version, String repoUrl) {
            this.groupId = notNull("groupId", groupId);
            this.artifactId = notNull("artifactId", artifactId);
            this.version = notNull("version", version);
            this.repoUrl = notNull("repoUrl", repoUrl);
        }

        /**
         * Retrieves the URL for the artifact in a Maven repository based on the provided information.
         *
         * @return A new {@link URL} representing the artifact's location.
         * @throws MalformedURLException If the URL cannot be constructed due to malformed input.
         */
        @Contract(" -> new")
        public @NotNull URL url() throws MalformedURLException {
            String repo = this.repoUrl;
            if (!repo.endsWith("/")) {
                repo += "/";
            }
            String metadataUrl = String.format("%s/%s/%s/%s/maven-metadata.xml", repo, this.groupId.replace(".", "/"), this.artifactId, this.version);

            try (InputStream is = new URL(metadataUrl).openStream()) {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                Document doc = db.parse(is);

                NodeList versionNodes = doc.getElementsByTagName("version");
                if (versionNodes.getLength() > 0) {
                    Element versionElement = (Element) versionNodes.item(0);
                    String latestVersion = versionElement.getTextContent();

                    if (latestVersion.endsWith("-SNAPSHOT")) {
                        NodeList snapshotVersionNodes = doc.getElementsByTagName("snapshotVersion");

                        for (int i = 0; i < snapshotVersionNodes.getLength(); i++) {
                            Element snapshotVersionElement = (Element) snapshotVersionNodes.item(i);
                            String extension = snapshotVersionElement.getElementsByTagName("extension").item(0).getTextContent();
                            if ("jar".equals(extension)) {
                                String jarValue = snapshotVersionElement.getElementsByTagName("value").item(0).getTextContent();

                                String jarFileName = String.format("%s-%s.jar", this.artifactId, jarValue);
                                return new URL(String.format("%s/%s/%s/%s/%s", repo, this.groupId.replace(".", "/"), this.artifactId, this.version, jarFileName));
                            }
                        }
                    } else {
                        String jarFileName = String.format("%s-%s.jar", this.artifactId, latestVersion);
                        return new URL(String.format("%s/%s/%s/%s", repo, this.groupId.replace(".", "/"), this.artifactId, jarFileName));
                    }
                }
            } catch (Exception e) {
                try {
                    repo += "%s/%s/%s/%s-%s.jar";

                    String url = String.format(repo, this.groupId.replace(".", "/"), this.artifactId, this.version, this.artifactId, this.version);
                    return new URL(url);
                } catch (Exception ignored) {
                    throw new RuntimeException("Unable to determine correct URL from Maven repository metadata.", e);
                }
            }

            throw new MalformedURLException("Unable to determine correct URL from Maven repository metadata.");
        }

        /**
         * Checks if this Dependency is equal to another object.
         *
         * @param o the object to compare
         * @return true if the objects are equal, false otherwise
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Dependency)) return false;
            Dependency that = (Dependency) o;
            return groupId.equals(that.groupId) && artifactId.equals(that.artifactId) && version.equals(that.version) && repoUrl.equals(that.repoUrl);
        }

        /**
         * Returns a string representation of this Dependency.
         *
         * @return a string representation of the Dependency
         */
        @Override
        public @NotNull String toString() {
            return "LibraryLoader.Dependency(" +
                    "groupId=" + this.groupId + ", " +
                    "artifactId=" + this.artifactId + ", " +
                    "version=" + this.version + ", " +
                    "repoUrl=" + this.repoUrl + ")";
        }

        /**
         * Converts a string representation of a Dependency back into a Dependency instance.
         *
         * @param string the string to transform
         * @return a new Dependency instance
         */
        @Contract("_ -> new")
        public static @NotNull Dependency fromString(@NotNull String string) {
            String[] arguments = string.split(Pattern.quote(", "));

            String groupId = arguments[0].substring(arguments[0].indexOf("=") + 1);
            String artifactId = arguments[1].substring(arguments[1].indexOf("=") + 1);
            String version = arguments[2].substring(arguments[2].indexOf("=") + 1);
            String repoUrl = arguments[3].substring(arguments[3].indexOf("=") + 1, arguments[3].lastIndexOf(")"));

            return new Dependency(groupId, artifactId, version, repoUrl);
        }
    }

    /**
     * Represents a relocated dependency with the specified group ID, artifact ID, version, repository URL,
     * and a list of relocations for class relocation during loading.
     */
    @NotNull
    public static class RelocatedDependency extends Dependency {

        /**
         * The list of relocations for class relocation during loading.
         */
        private final List<Relocation> relocations;

        /**
         * Constructs a new RelocatedDependency with the given group ID, artifact ID, version,
         * repository URL, and a list of relocations for class relocation.
         *
         * @param groupId     the group ID of the dependency
         * @param artifactId  the artifact ID of the dependency
         * @param version     the version of the dependency
         * @param repoUrl     the URL of the repository where the dependency is hosted
         * @param relocations the list of relocations for class relocation
         */
        public RelocatedDependency(String groupId, String artifactId, String version, String repoUrl, List<Relocation> relocations) {
            super(groupId, artifactId, version, repoUrl);
            this.relocations = notNull("relocations", relocations);
        }

        /**
         * Returns the list of relocations for class relocation during loading.
         *
         * @return the list of relocations
         */
        public List<Relocation> getRelocations() {
            return relocations;
        }
    }

    @Contract(pure = true)
    @Override
    public @NotNull String toString() {
        return "LibraryLoader{" +
                "dataFolder=" + dataFolder +
                ", dependencyList=" + dependencyList +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LibraryLoader that = (LibraryLoader) o;
        return Objects.equals(logger, that.logger) && Objects.equals(dataFolder, that.dataFolder) && Objects.equals(getDependencyList(), that.getDependencyList());
    }

    @Override
    public int hashCode() {
        return Objects.hash(logger, dataFolder, getDependencyList());
    }

    /**
     * Throw IllegalArgumentException if the value is null.
     *
     * @param name  the parameter name
     * @param value the value that should not be null
     * @param <T>   the value type
     * @return the value
     * @throws IllegalArgumentException if value is null
     */
    @Contract(value = "_, null -> fail; _, !null -> param2", pure = true)
    private static <T> @NotNull T notNull(final String name, final T value) {
        if (value == null) {
            throw new IllegalArgumentException(name + " can not be null");
        }
        return value;
    }
}