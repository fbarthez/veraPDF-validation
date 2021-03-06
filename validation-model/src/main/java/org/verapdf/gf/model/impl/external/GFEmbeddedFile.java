/**
 * This file is part of validation-model, a module of the veraPDF project.
 * Copyright (c) 2015, veraPDF Consortium <info@verapdf.org>
 * All rights reserved.
 *
 * validation-model is free software: you can redistribute it and/or modify
 * it under the terms of either:
 *
 * The GNU General public license GPLv3+.
 * You should have received a copy of the GNU General Public License
 * along with validation-model as the LICENSE.GPL file in the root of the source
 * tree.  If not, see http://www.gnu.org/licenses/ or
 * https://www.gnu.org/licenses/gpl-3.0.en.html.
 *
 * The Mozilla Public License MPLv2+.
 * You should have received a copy of the Mozilla Public License along with
 * validation-model as the LICENSE.MPL file in the root of the source tree.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package org.verapdf.gf.model.impl.external;

import org.verapdf.as.ASAtom;
import org.verapdf.core.VeraPDFException;
import org.verapdf.cos.COSKey;
import org.verapdf.cos.COSStream;
import org.verapdf.gf.model.GFModelParser;
import org.verapdf.gf.model.impl.containers.StaticContainers;
import org.verapdf.gf.model.impl.pd.colors.GFPDSeparation;
import org.verapdf.gf.model.impl.pd.util.TaggedPDFRoleMapHelper;
import org.verapdf.model.external.EmbeddedFile;
import org.verapdf.model.operator.Glyph;
import org.verapdf.model.pdlayer.PDColorSpace;
import org.verapdf.model.pdlayer.PDFont;
import org.verapdf.pd.PDDocument;
import org.verapdf.pd.font.FontProgram;
import org.verapdf.pd.font.cmap.CMap;
import org.verapdf.pd.structure.PDStructureNameSpace;
import org.verapdf.pdfa.PDFAValidator;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.pdfa.results.ValidationResult;
import org.verapdf.pdfa.validation.validators.ValidatorFactory;
import org.verapdf.tools.StaticResources;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Maksim Bezrukov
 */
public class GFEmbeddedFile extends GFExternal implements EmbeddedFile {

	private static final Logger LOGGER = Logger.getLogger(GFEmbeddedFile.class.getCanonicalName());

	/** Type name for {@code PBoxEmbeddedFile} */
	public static final String EMBEDDED_FILE_TYPE = "EmbeddedFile";

	private final COSStream stream;

	public GFEmbeddedFile(COSStream stream) {
		super(EMBEDDED_FILE_TYPE);
		this.stream = stream;
	}

	@Override
	public String getSubtype() {
		if (this.stream != null) {
			ASAtom s = this.stream.getNameKey(ASAtom.SUBTYPE);
			return s == null ? null : s.getValue();
		}
		return null;
	}

	@Override
	public Boolean getisValidPDFA12() {
		if (this.stream == null) {
			return Boolean.TRUE;
		}
		boolean retVal = false;
		saveStaticContainersState();
		try (InputStream unfilteredStream = stream.getData(COSStream.FilterFlags.DECODE)) {
			retVal = isValidPdfaStream(unfilteredStream, PDFAFlavour.PDFA_1_B);
			if (!retVal) {
				unfilteredStream.reset();
				retVal = isValidPdfaStream(unfilteredStream, PDFAFlavour.PDFA_2_B);
			}
		} catch (VeraPDFException | IOException e) {
			LOGGER.log(Level.FINE, "Exception during validation of embedded file", e);
		}
		restoreSavedSCState();
		return Boolean.valueOf(retVal);
	}

	private static boolean isValidPdfaStream(final InputStream toValidate, final PDFAFlavour flavour)
			throws VeraPDFException {
		try (GFModelParser parser = GFModelParser.createModelWithFlavour(toValidate, flavour)) {
			PDFAValidator validator1b = ValidatorFactory.createValidator(flavour, false, 1);
			ValidationResult result1b = validator1b.validate(parser);
			parser.close();
			return result1b.isCompliant();
		}
	}

	// We need to save data from StaticContainers before validating embedded
	// documents
	private PDDocument document;
	private PDFAFlavour flavour;
	private TaggedPDFRoleMapHelper roleMapHelper;
	private Map<String, List<GFPDSeparation>> separations;
	private List<String> inconsistentSeparations;
	private Map<String, PDColorSpace> cachedColorSpaces;
	private Set<COSKey> fileSpecificationKeys;
	private Stack<COSKey> transparencyVisitedContentStreams;
	private Map<String, PDFont> cachedPDFonts;
	private Map<String, Glyph> cachedGlyphs;
	private boolean validPDF;

	// StaticResources have to be saved too
	private Map<String, CMap> cMapCache;
	private Map<COSKey, PDStructureNameSpace> structureNameSpaceCache;
	private Map<String, FontProgram> cachedFonts;

	private void saveStaticContainersState() {
		this.document = StaticContainers.getDocument();
		this.flavour = StaticContainers.getFlavour();
		this.separations = StaticContainers.separations;
		this.inconsistentSeparations = StaticContainers.inconsistentSeparations;
		this.cachedColorSpaces = StaticContainers.cachedColorSpaces;
		this.cachedPDFonts = StaticContainers.cachedFonts;
		this.roleMapHelper = StaticContainers.roleMapHelper;
		this.fileSpecificationKeys = StaticContainers.fileSpecificationKeys;
		this.transparencyVisitedContentStreams = StaticContainers.transparencyVisitedContentStreams;
		this.validPDF = StaticContainers.validPDF;
		this.cachedGlyphs = StaticContainers.cachedGlyphs;

		this.cMapCache = new HashMap<>(StaticResources.cMapCache);
		this.structureNameSpaceCache = new HashMap<>(StaticResources.structureNameSpaceCache);
		this.cachedFonts = new HashMap<>(StaticResources.cachedFonts);
	}

	private void restoreSavedSCState() {
		StaticContainers.setDocument(this.document);
		StaticContainers.setFlavour(this.flavour);
		StaticContainers.separations = this.separations;
		StaticContainers.inconsistentSeparations = this.inconsistentSeparations;
		StaticContainers.cachedColorSpaces = this.cachedColorSpaces;
		StaticContainers.cachedFonts = this.cachedPDFonts;
		StaticContainers.roleMapHelper = this.roleMapHelper;
		StaticContainers.fileSpecificationKeys = this.fileSpecificationKeys;
		StaticContainers.transparencyVisitedContentStreams = this.transparencyVisitedContentStreams;
		StaticContainers.validPDF = this.validPDF;
		StaticContainers.cachedGlyphs = this.cachedGlyphs;

		StaticResources.cMapCache = this.cMapCache;
		StaticResources.structureNameSpaceCache = this.structureNameSpaceCache;
		StaticResources.cachedFonts = this.cachedFonts;
	}

}
