/**
 * 
 */
package de.sfdccommander.controller;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import de.sfdccommander.controller.helper.CodeFileNameFilter;
import de.sfdccommander.controller.helper.CommanderException;
import de.sfdccommander.controller.helper.XmlFileNameFilter;
import de.sfdccommander.controller.helper.XsltFileNameFilter;
import de.sfdccommander.model.CommanderConfig;
import de.sfdccommander.viewer.SfdcCommander;

/**
 * @author jochen
 * 
 */
public class HtmlRenderer {

    /**
     * Config for renderings.
     */
    private final CommanderConfig config;

    /**
     * 
     */
    private SfdcCommander commander;

    /**
     * @param aConfig
     *            Config for renderings.
     */
    public HtmlRenderer(final CommanderConfig aConfig) {
        this.config = aConfig;
    }

    public void generateOutput() throws CommanderException {
        commander = SfdcCommander.getInstance();
        File[] transformFiles;

        File transformerFolder = new File("config/transformer");

        // prepare HTML output folder
        File outputFolder = new File(
                config.getRenderPath() + "/" + config.getSfSystemname());
        if (outputFolder.exists()) {
            deleteDirectory(outputFolder);
        }
        outputFolder.mkdirs();

        if (transformerFolder.isDirectory()) {

            // copy css, images, fonts and js
            String[] helperFolder = { "css", "images", "script", "fonts" };

            for (String folder : helperFolder) {
                try {
                    copyFolder(
                            new File(transformerFolder.getAbsolutePath() + "/"
                                    + folder),
                            new File(outputFolder.getAbsolutePath() + "/"
                                    + folder));
                } catch (IOException e) {
                    throw new CommanderException("Could not copy folder "
                            + transformerFolder.getAbsolutePath() + "/" + folder
                            + " to " + outputFolder.getAbsolutePath() + "/"
                            + folder);
                }
            }

            // copy documents if available
            File documentsFolder = new File(
                    config.getSfSystemname() + "/unpackaged/documents");
            if (documentsFolder.exists()) {
                commander.info("Copying documents");
                try {
                    copyFolder(documentsFolder, outputFolder);
                } catch (IOException e) {
                    throw new CommanderException("Could not copy folder "
                            + documentsFolder.getAbsolutePath() + " to "
                            + outputFolder.getAbsolutePath());
                }
            }

            // get xslt files
            transformFiles = transformerFolder
                    .listFiles(new XsltFileNameFilter());

            for (File transformer : transformFiles) {
                // create folder for current transformation file
                String tmpTransformerName = transformer.getName().substring(0,
                        transformer.getName().lastIndexOf("."));
                File tmpOutputFolder = new File(outputFolder.getAbsolutePath()
                        + "/" + tmpTransformerName);
                tmpOutputFolder.mkdirs();

                // get folder with sfdc source files
                File sourceFolder = new File(config.getSfSystemname()
                        + "/unpackaged/" + tmpTransformerName);

                File targetFolder;

                if (sourceFolder.exists()) {
                    // join roles and territories
                    if (tmpTransformerName.equals("roles")
                            || tmpTransformerName.equals("territories")) {
                        mergeFiles(tmpTransformerName, sourceFolder);
                    }
                    if (!tmpTransformerName.equals("lists")) {
                        commander.info("Generating output for "
                                + transformer.getName());
                        targetFolder = new File(
                                config.getSfSystemname() + "/unpackaged/lists");
                        targetFolder.mkdirs();
                        generateFileList(tmpTransformerName, sourceFolder,
                                targetFolder);
                        // generate html files
                        for (File xmlFile : sourceFolder
                                .listFiles(new XmlFileNameFilter())) {
                            render(transformer, xmlFile,
                                    new File(tmpOutputFolder.getAbsolutePath()
                                            + "/"
                                            + xmlFile.getName().substring(0,
                                                    xmlFile.getName()
                                                            .lastIndexOf("."))
                                            + ".html"));
                        }
                        if (tmpTransformerName.equals("triggers")
                                || tmpTransformerName.equals("classes")
                                || tmpTransformerName.equals("pages")
                                || tmpTransformerName.equals("scontrols")) {
                            for (File codeFile : sourceFolder
                                    .listFiles(new CodeFileNameFilter())) {
                                File codeTargetFile = new File(
                                        tmpOutputFolder.getAbsolutePath() + "/"
                                                + codeFile.getName());
                                try {
                                    copyFile(codeFile, codeTargetFile, 1, true);
                                } catch (IOException e) {
                                    throw new CommanderException(
                                            "Could not copy file "
                                                    + codeFile.getAbsolutePath()
                                                    + " to "
                                                    + codeTargetFile
                                                            .getAbsolutePath(),
                                            e);
                                }
                            }
                        }
                    }

                }
            }
            // Generate Lists
            File listFolder = new File(
                    config.getSfSystemname() + "/unpackaged/lists");
            generateFileList("lists", listFolder,
                    new File(config.getSfSystemname()));
            File listTransformer = new File(
                    transformerFolder.getAbsolutePath() + "/lists.xslt");
            for (File xmlFile : listFolder.listFiles()) {
                render(listTransformer, xmlFile,
                        new File(outputFolder.getAbsolutePath() + "/lists/"
                                + xmlFile.getName().substring(0,
                                        xmlFile.getName().lastIndexOf("."))
                        + ".html"));
            }

            // Generate Index
            File indexTransformer = new File(
                    transformerFolder.getAbsolutePath() + "/index.xsl");
            File indexSource = new File(
                    config.getSfSystemname() + "/lists.xml");
            File indexOutput = new File(
                    outputFolder.getAbsolutePath() + "/index.html");
            render(indexTransformer, indexSource, indexOutput);
            commander.info("Output generated");
        }
    }

    /**
     * @param xslFile
     *            Transformation file for rendering.
     * @param xmlFile
     *            data file for rendering.
     * @param htmlFile
     *            output file for rendering.
     * @throws CommanderException
     */
    public final void render(final File xslFile, final File xmlFile,
            final File htmlFile) throws CommanderException {

        TransformerFactory tfactory = TransformerFactory.newInstance();
        try {
            FileOutputStream fos = new FileOutputStream(htmlFile);

            // Create a transformer for the stylesheet.
            Transformer transformer;
            transformer = tfactory.newTransformer(new StreamSource(xslFile));

            // Transform the source XML to System.out.
            transformer.transform(new StreamSource(xmlFile),
                    new StreamResult(fos));
            fos.close();
        } catch (TransformerConfigurationException e) {
            throw new CommanderException("Could not configure transformer "
                    + xslFile.getAbsolutePath(), e);
        } catch (TransformerException e) {
            throw new CommanderException(
                    "Could not transform file " + xmlFile.getAbsolutePath(), e);
        } catch (FileNotFoundException e) {
            throw new CommanderException(
                    "Could not create new file " + htmlFile.getAbsolutePath()
                            + ". another blocked file or folder might already exist.",
                    e);
        } catch (IOException e) {
            throw new CommanderException(
                    "Could not save file " + htmlFile.getAbsolutePath(), e);
        }
    }

    /**
     * @param entity
     *            entity for merge process.
     * @param sourceFolder
     *            source folder for merge process.
     * @throws CommanderException
     */
    public final void mergeFiles(final String entity, final File sourceFolder)
            throws CommanderException {

        File allRecordsFile = new File(sourceFolder.getAbsolutePath() + "/"
                + "all_" + entity + ".xml");

        try {
            FileOutputStream fos = new FileOutputStream(allRecordsFile);

            fos.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n")
                    .getBytes());
            fos.write(("<" + entity
                    + " xmlns=\"http://soap.sforce.com/2006/04/metadata\">\r\n")
                            .getBytes());

            String actLine;
            for (File recordFile : sourceFolder.listFiles()) {
                if (!recordFile.getAbsolutePath()
                        .equals(allRecordsFile.getAbsolutePath())) {
                    InputStreamReader isr = new InputStreamReader(
                            new FileInputStream(recordFile));
                    BufferedReader br = new BufferedReader(isr);

                    // Read first line with xml version
                    actLine = br.readLine();
                    while ((actLine = br.readLine()) != null) {
                        if (actLine.contains(
                                " xmlns=\"http://soap.sforce.com/2006/04/metadata\"")) {
                            fos.write(actLine.replace(
                                    " xmlns=\"http://soap.sforce.com/2006/04/metadata\"",
                                    "").concat("\r\n").getBytes());
                        } else {
                            fos.write(actLine.concat("\r\n").getBytes());
                        }

                    }
                    br.close();
                    recordFile.delete();
                }
            }
            fos.write(("</" + entity + ">").getBytes());
            fos.close();
        } catch (FileNotFoundException e) {
            throw new CommanderException(
                    "Could not find either " + allRecordsFile.getAbsolutePath()
                            + " or one of the entity files.",
                    e);
        } catch (IOException e) {
            throw new CommanderException(
                    "Could not write " + allRecordsFile.getAbsolutePath()
                            + " or read one of the entity files.",
                    e);
        }
    }

    public void generateFileList(final String entity, final File sourceFolder,
            final File targetFolder) throws CommanderException {
        File output = null;
        try {
            output = new File(
                    targetFolder.getAbsolutePath() + "/" + entity + ".xml");
            FileOutputStream fos = new FileOutputStream(output);

            fos.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n")
                    .getBytes());
            fos.write(
                    ("<Files xmlns=\"http://soap.sforce.com/2006/04/metadata\">\r\n")
                            .getBytes());
            fos.write(("<entity>" + entity + "</entity>\r\n").getBytes());
            fos.write(("<system>" + config.getSfSystemname() + "</system>\r\n")
                    .getBytes());

            String cutFileName;
            for (File actFile : sourceFolder
                    .listFiles(new XmlFileNameFilter())) {
                cutFileName = actFile.getName().substring(0,
                        actFile.getName().lastIndexOf("."));
                fos.write(("<file>" + cutFileName + "</file>\r\n").getBytes());
            }

            fos.write(("</Files>").getBytes());
            fos.close();
        } catch (IOException e) {
            throw new CommanderException(
                    "Could not generate file-list " + output.getAbsolutePath(),
                    e);
        }

    }

    /**
     * @param path
     *            File path to delete.
     * @return if path has been deleted.
     */
    public static boolean deleteDirectory(final File path) {
        if (path.exists()) {
            File[] files = path.listFiles();
            for (int i = 0; i < files.length; i++) {
                if (files[i].isDirectory()) {
                    deleteDirectory(files[i]);
                } else {
                    files[i].delete();
                }
            }
        }
        return (path.delete());
    }

    /**
     * @param src
     *            Source File
     * @param dest
     *            Destination File
     * @param bufSize
     *            Size of byte[] buffer
     * @param force
     *            Overwrite Flag
     * @throws IOException
     *             Cannot overwrite existing file
     */
    private void copyFile(File src, File dest, int bufSize, boolean force)
            throws IOException {
        if (dest.exists()) {
            if (force) {
                dest.delete();
            } else {
                throw new IOException(
                        "Cannot overwrite existing file: " + dest.toString());
            }
        }
        byte[] buffer = new byte[bufSize];
        int read = 0;
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new FileInputStream(src);
            out = new FileOutputStream(dest);
            while (true) {
                read = in.read(buffer);
                if (read == -1) {
                    // -1 bedeutet EOF
                    break;
                }
                out.write(buffer, 0, read);
            }
        } finally {
            // Sicherstellen, dass die Streams auch
            // bei einem throw geschlossen werden.
            // Falls in null ist, ist out auch null!
            if (in != null) {
                // Falls tatsächlich in.close() und out.close()
                // Exceptions werfen, die jenige von 'out' geworfen wird.
                try {
                    in.close();
                } finally {
                    if (out != null) {
                        out.close();
                    }
                }
            }
        }
    }

    /**
     * @param src
     *            source folder.
     * @param dest
     *            destination folder.
     * @throws IOException
     *             Exception, if copy not possible.
     */
    public static void copyFolder(final File src, final File dest)
            throws IOException {

        if (src.isDirectory()) {

            // if directory not exists, create it
            if (!dest.exists()) {
                dest.mkdir();
                System.out.println(
                        "Directory copied from " + src + "  to " + dest);
            }

            // list all the directory contents
            String[] files;

            files = src.list();

            for (String file : files) {
                // construct the src and dest file structure
                File srcFile = new File(src, file);
                File destFile = new File(dest, file);
                // recursive copy
                copyFolder(srcFile, destFile);
            }

        } else {
            // if file, then copy it
            // Use bytes stream to support all file types
            InputStream in = new FileInputStream(src);
            OutputStream out = new FileOutputStream(dest);

            byte[] buffer = new byte[1024];

            int length;
            // copy the file content in bytes
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }

            in.close();
            out.close();
            System.out.println("File copied from " + src + " to " + dest);
        }
    }
}
