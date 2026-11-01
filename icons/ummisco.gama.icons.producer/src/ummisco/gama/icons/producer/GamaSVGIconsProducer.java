/*******************************************************************************************************
 *
 * GamaSVGIconsProducer.java, in ummisco.gama.icons.producer, is part of the source code of the GAMA modeling and
 * simulation platform (v.2025-03).
 *
 * (c) 2007-2025 UMI 209 UMMISCO IRD/SU & Partners (IRIT, MIAT, ESPACE-DEV, CTU)
 *
 * Visit https://github.com/gama-platform/gama for license information and contacts.
 *
 ********************************************************************************************************/
package ummisco.gama.icons.producer;

import java.io.File;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * The Class SvgCombiner. Combine multiple SVG files into a single SVG file with symbols. This is useful for putting all
 * GAMA (version > 2025.06 ) icons in one file. Run this class everytime a SVG icon is added/removed/modified in the SVG
 * folder
 */
public class GamaSVGIconsProducer {

	/**
	 * The main method.
	 *
	 * @param args
	 *            the arguments
	 */
	public static void main(final String[] args) {

		try {
			combineSvgs(args.length <= 1 ? "svg/light" : args[0], args.length <= 2 ? "light.svg" : args[1]);
			combineSvgs(args.length <= 1 ? "svg/dark" : args[0], args.length <= 2 ? "dark.svg" : args[1]);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Combine svgs.
	 *
	 * @param sourceDirPath
	 *            the source dir path
	 * @param outputFilePath
	 *            the output file path
	 * @throws Exception
	 *             the exception
	 */
	public static void combineSvgs(final String sourceDirPath, final String outputFilePath) throws Exception {
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		dbFactory.setNamespaceAware(true);
		dbFactory.setValidating(false); // Optimisation
		try {
			dbFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		} catch (ParserConfigurationException e) {}

		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document masterDoc = dBuilder.newDocument();

		Element rootElement = masterDoc.createElementNS("http://www.w3.org/2000/svg", "svg");
		rootElement.setAttribute("xmlns", "http://www.w3.org/2000/svg");
		rootElement.setAttribute("style", "display: none;");
		masterDoc.appendChild(rootElement);

		Path startPath = Paths.get(sourceDirPath);

		try (Stream<Path> stream = Files.walk(startPath)) {
			stream.filter(p -> p.toString().toLowerCase().endsWith(".svg"))
					.filter(p -> !p.getFileName().toString().equals(outputFilePath)).forEach(path -> {
						try {
							processSvgFile(path, masterDoc, rootElement, startPath);
						} catch (Exception e) {
							System.err.println("Impossible de traiter : " + path + " -> " + e.getMessage());
						}
					});
		}

		// Configuration du Transformer pour la MINIFICATION
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer transformer = transformerFactory.newTransformer();

		// On désactive explicitement l'indentation
		transformer.setOutputProperty(OutputKeys.INDENT, "no");
		// On supprime la déclaration XML (<?xml...?>) pour gagner encore un peu de place (optionnel)
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

		DOMSource source = new DOMSource(masterDoc);
		StreamResult result = new StreamResult(new File(outputFilePath));

		transformer.transform(source, result);
	}

	/**
	 * Process svg file.
	 *
	 * @param path
	 *            the path
	 * @param masterDoc
	 *            the master doc
	 * @param rootElement
	 *            the root element
	 * @param startPath
	 *            the start path
	 * @throws Exception
	 *             the exception
	 */
	private static void processSvgFile(final Path path, final Document masterDoc, final Element rootElement,
			final Path startPath) throws Exception {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(true);
		dbf.setValidating(false);
		try {
			dbf.setFeature("http://xml.org/sax/features/validation", false);
			dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
			dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		} catch (ParserConfigurationException e) {}

		DocumentBuilder builder = dbf.newDocumentBuilder();
		builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));

		Document subDoc = builder.parse(path.toFile());
		Element subRoot = subDoc.getDocumentElement();

		// Nettoyage agressif (Espaces, Commentaires, Titres, IDs de groupes)
		cleanNode(subRoot);

		Element symbol = masterDoc.createElementNS("http://www.w3.org/2000/svg", "symbol");
		String relativePath = startPath.relativize(path).toString();

		// Remplacement des séparateurs de dossier par un standard (optionnel mais propre pour les IDs)
		// symbol.setAttribute("id", relativePath.replace(File.separator, "/"));
		symbol.setAttribute("id", relativePath);

		NodeList children = subRoot.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			Node importedNode = masterDoc.importNode(child, true);
			symbol.appendChild(importedNode);
		}

		rootElement.appendChild(symbol);
		System.out.println("Ajouté : " + relativePath);
	}

	/**
	 * Nettoyage récursif pour minification
	 */
	private static void cleanNode(final Node node) {
		NodeList childNodes = node.getChildNodes();

		for (int i = childNodes.getLength() - 1; i >= 0; i--) {
			Node child = childNodes.item(i);
			short type = child.getNodeType();

			// 1. Supprimer les commentaires if (type == Node.COMMENT_NODE)

			// 2. Supprimer les nœuds de texte qui ne contiennent que du vide (sauts de ligne, tabs)
			switch (type) {
				case Node.COMMENT_NODE:
					node.removeChild(child);
					continue;
				case Node.TEXT_NODE: {
					String text = child.getTextContent();
					if (text != null && text.trim().isEmpty()) { node.removeChild(child); }
					continue;
				}
				case Node.ELEMENT_NODE: {
					Element elem = (Element) child;
					String tagName = elem.getTagName();
					// 3. Supprimer les balises <title>
					if ("title".equalsIgnoreCase(tagName) || tagName.endsWith(":title")) {
						node.removeChild(child);
						continue;
					}
					// 4. Supprimer l'attribut 'id' sur les groupes <g>
					if (("g".equalsIgnoreCase(tagName) || tagName.endsWith(":g")) && elem.hasAttribute("id")) {
						elem.removeAttribute("id");
					}
					// Récursion
					cleanNode(child);
					break;
				}
				default:
					break;
			}
		}
	}
}