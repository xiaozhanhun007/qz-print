package qz.printer.action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qz.printer.PrintOptions;
import qz.printer.PrintOutput;
import qz.utils.SystemUtilities;

import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.ResolutionSyntax;
import javax.print.attribute.Size2DSyntax;
import javax.print.attribute.standard.*;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.Arrays;
import java.util.List;

public abstract class PrintPixel {

    private static final Logger log = LoggerFactory.getLogger(PrintPixel.class);

    private static final List<Integer> MAC_BAD_IMAGE_TYPES = Arrays.asList(BufferedImage.TYPE_BYTE_BINARY, BufferedImage.TYPE_CUSTOM);


    protected PrintRequestAttributeSet applyDefaultSettings(PrintOptions.Pixel pxlOpts, PageFormat page) {
        PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();

        // Java prints using inches at 72dpi
        final float CONVERT = 72 * pxlOpts.getUnits().toInch();
        final float DENSITY = (float)pxlOpts.getDensity() * pxlOpts.getUnits().fromInch();

        //apply general attributes
        if (pxlOpts.getColorType() != null) {
            attributes.add(pxlOpts.getColorType().getChromatic());
        }
        if (pxlOpts.isDuplex()) {
            attributes.add(Sides.DUPLEX);
        }
        if (pxlOpts.getOrientation() != null) {
            attributes.add(pxlOpts.getOrientation().getAsAttribute());
        }

        if (pxlOpts.getSize() != null) {
            attributes.add(MediaSize.findMedia((float)pxlOpts.getSize().getWidth() / CONVERT, (float)pxlOpts.getSize().getHeight() / CONVERT, pxlOpts.getUnits().getMediaSizeUnits()));
        } else {
            attributes.add(MediaSize.findMedia((float)page.getWidth() / 72f, (float)page.getHeight() / 72f, Size2DSyntax.INCH));
        }

        //TODO - set paper thickness
        //TODO - set printer tray


        log.trace("DPI: {}", DENSITY);
        if (DENSITY > 0) {
            attributes.add(new PrinterResolution((int)DENSITY, (int)DENSITY, ResolutionSyntax.DPI));
        }

        //apply sizing and margins
        Paper paper = new Paper();

        float pageX = 0f;
        float pageY = 0f;
        float pageW = (float)page.getWidth();
        float pageH = (float)page.getHeight();

        //page size
        if (pxlOpts.getSize() != null && pxlOpts.getSize().getWidth() > 0 && pxlOpts.getSize().getHeight() > 0) {
            pageW = (float)pxlOpts.getSize().getWidth() * CONVERT;
            pageH = (float)pxlOpts.getSize().getHeight() * CONVERT;

            paper.setSize(pageW, pageH);
        }

        //margins
        if (pxlOpts.getMargins() != null) {
            pageX += pxlOpts.getMargins().left() * CONVERT;
            pageY += pxlOpts.getMargins().top() * CONVERT;
            pageW -= (pxlOpts.getMargins().right() + pxlOpts.getMargins().left()) * CONVERT;
            pageH -= (pxlOpts.getMargins().bottom() + pxlOpts.getMargins().top()) * CONVERT;
        }

        log.trace("Drawable area: {},{}:{},{}", pageX, pageY, pageW, pageH);
        if (pageW > 0 && pageH > 0) {
            paper.setImageableArea(pageX, pageY, pageW, pageH);
            page.setPaper(paper);
        } else {
            log.warn("Could not apply custom size, using printer default");
        }

        log.trace("{}", Arrays.toString(attributes.toArray()));

        return attributes;
    }


    protected void printCopies(PrintOutput output, PrintOptions.Pixel pxlOpts, PrinterJob job, PrintRequestAttributeSet attributes) throws PrinterException {
        log.info("Starting printing ({} copies)", pxlOpts.getCopies());

        CopiesSupported cSupport = (CopiesSupported)output.getPrintService()
                .getSupportedAttributeValues(Copies.class, output.getPrintService().getSupportedDocFlavors()[0], attributes);

        if (cSupport != null && cSupport.contains(pxlOpts.getCopies())) {
            attributes.add(new Copies(pxlOpts.getCopies()));
            job.print(attributes);
        } else {
            for(int i = 0; i < pxlOpts.getCopies(); i++) {
                job.print(attributes);
            }
        }
    }

    /**
     * FIXME:  Temporary fix for OS X 10.10 hard crash.
     * See https://github.com/qzind/qz-print/issues/75
     */
    protected BufferedImage fixColorModel(BufferedImage imgToPrint) {
        if (SystemUtilities.isMac()) {
            if (MAC_BAD_IMAGE_TYPES.contains(imgToPrint.getType())) {
                BufferedImage sanitizedImage;
                ColorModel cm = imgToPrint.getColorModel();

                if (cm instanceof IndexColorModel) {
                    log.info("Image converted to 256 colors for OSX 10.10 Workaround");
                    sanitizedImage = new BufferedImage(imgToPrint.getWidth(), imgToPrint.getHeight(), BufferedImage.TYPE_BYTE_INDEXED, (IndexColorModel)cm);
                } else {
                    log.info("Image converted to ARGB for OSX 10.10 Workaround");
                    sanitizedImage = new BufferedImage(imgToPrint.getWidth(), imgToPrint.getHeight(), BufferedImage.TYPE_INT_ARGB);
                }

                sanitizedImage.createGraphics().drawImage(imgToPrint, 0, 0, null);
                imgToPrint = sanitizedImage;
            }
        }

        return imgToPrint;
    }

}