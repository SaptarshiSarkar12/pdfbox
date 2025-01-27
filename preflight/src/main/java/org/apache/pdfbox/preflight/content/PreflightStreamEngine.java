/*****************************************************************************
 * 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * 
 ****************************************************************************/

package org.apache.pdfbox.preflight.content;

import java.awt.color.ColorSpace;
import static org.apache.pdfbox.preflight.PreflightConstants.ERROR_GRAPHIC_INVALID_COLOR_SPACE_CMYK;
import static org.apache.pdfbox.preflight.PreflightConstants.ERROR_GRAPHIC_INVALID_COLOR_SPACE_MISSING;
import static org.apache.pdfbox.preflight.PreflightConstants.ERROR_GRAPHIC_INVALID_COLOR_SPACE_RGB;
import static org.apache.pdfbox.preflight.PreflightConstants.ERROR_GRAPHIC_TOO_MANY_GRAPHIC_STATES;
import static org.apache.pdfbox.preflight.PreflightConstants.ERROR_GRAPHIC_UNEXPECTED_VALUE_FOR_KEY;
import static org.apache.pdfbox.preflight.PreflightConstants.MAX_GRAPHIC_STATES;

import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.color.PDCIEBasedColorSpace;
import org.apache.pdfbox.pdmodel.graphics.color.PDColorSpace;
import org.apache.pdfbox.pdmodel.graphics.color.PDICCBased;
import org.apache.pdfbox.pdmodel.graphics.color.PDSeparation;
import org.apache.pdfbox.preflight.PreflightConfiguration;
import org.apache.pdfbox.preflight.PreflightContext;
import org.apache.pdfbox.preflight.ValidationResult.ValidationError;
import org.apache.pdfbox.preflight.exception.ValidationException;
import org.apache.pdfbox.preflight.graphic.ColorSpaceHelper;
import org.apache.pdfbox.preflight.graphic.ColorSpaceHelperFactory;
import org.apache.pdfbox.preflight.graphic.ColorSpaceHelperFactory.ColorSpaceRestriction;
import org.apache.pdfbox.preflight.graphic.ColorSpaces;
import org.apache.pdfbox.preflight.graphic.ICCProfileWrapper;
import org.apache.pdfbox.preflight.utils.FilterHelper;
import org.apache.pdfbox.preflight.utils.RenderingIntents;
import org.apache.pdfbox.contentstream.operator.DrawObject;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.OperatorName;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingColorN;
import org.apache.pdfbox.contentstream.operator.color.SetStrokingColorN;
import org.apache.pdfbox.contentstream.operator.text.BeginText;
import org.apache.pdfbox.contentstream.operator.state.Concatenate;
import org.apache.pdfbox.contentstream.operator.text.EndText;
import org.apache.pdfbox.contentstream.operator.state.Restore;
import org.apache.pdfbox.contentstream.operator.state.Save;
import org.apache.pdfbox.contentstream.operator.text.MoveText;
import org.apache.pdfbox.contentstream.operator.text.MoveTextSetLeading;
import org.apache.pdfbox.contentstream.operator.text.NextLine;
import org.apache.pdfbox.contentstream.operator.text.SetCharSpacing;
import org.apache.pdfbox.contentstream.operator.text.SetFontAndSize;
import org.apache.pdfbox.contentstream.operator.text.SetTextHorizontalScaling;
import org.apache.pdfbox.contentstream.operator.state.SetLineCapStyle;
import org.apache.pdfbox.contentstream.operator.state.SetLineDashPattern;
import org.apache.pdfbox.contentstream.operator.state.SetLineJoinStyle;
import org.apache.pdfbox.contentstream.operator.state.SetLineWidth;
import org.apache.pdfbox.contentstream.operator.state.SetMatrix;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingDeviceCMYKColor;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingColor;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingColorSpace;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingDeviceGrayColor;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingDeviceRGBColor;
import org.apache.pdfbox.contentstream.operator.color.SetStrokingDeviceCMYKColor;
import org.apache.pdfbox.contentstream.operator.color.SetStrokingColor;
import org.apache.pdfbox.contentstream.operator.color.SetStrokingColorSpace;
import org.apache.pdfbox.contentstream.operator.color.SetStrokingDeviceGrayColor;
import org.apache.pdfbox.contentstream.operator.color.SetStrokingDeviceRGBColor;
import org.apache.pdfbox.contentstream.operator.state.SetGraphicsStateParameters;
import org.apache.pdfbox.contentstream.operator.text.SetTextLeading;
import org.apache.pdfbox.contentstream.operator.text.SetTextRenderingMode;
import org.apache.pdfbox.contentstream.operator.text.SetTextRise;
import org.apache.pdfbox.contentstream.operator.text.SetWordSpacing;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceGray;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;

/**
 * This class inherits from org.apache.pdfbox.util.PDFStreamEngine to allow the validation of specific rules in
 * ContentStream.
 */
public abstract class PreflightStreamEngine extends PDFStreamEngine
{
    private enum ColorSpaceType
    {
        RGB, CMYK, ALL
    }

    protected PreflightContext context = null;
    protected PDPage processedPage = null;

    public PreflightStreamEngine(PreflightContext context, PDPage page)
    {
        this.context = context;
        this.processedPage = page;

        // Graphics operators
        addOperator(new SetLineWidth(this));
        addOperator(new Concatenate(this));

        addOperator(new SetStrokingColorSpace(this));
        addOperator(new SetNonStrokingColorSpace(this));
        addOperator(new SetLineDashPattern(this));
        addOperator(new DrawObject(this));

        addOperator(new SetLineJoinStyle(this));
        addOperator(new SetLineCapStyle(this));
        addOperator(new SetStrokingDeviceCMYKColor(this));
        addOperator(new SetNonStrokingDeviceCMYKColor(this));

        addOperator(new SetNonStrokingDeviceRGBColor(this));
        addOperator(new SetStrokingDeviceRGBColor(this));

        addOperator(new SetNonStrokingDeviceGrayColor(this));
        addOperator(new SetStrokingDeviceGrayColor(this));

        addOperator(new SetStrokingColor(this));
        addOperator(new SetStrokingColorN(this));
        addOperator(new SetNonStrokingColor(this));
        addOperator(new SetNonStrokingColorN(this));

        // Graphics state
        addOperator(new Restore(this));
        addOperator(new Save(this));

        // Text operators
        addOperator(new BeginText(this));
        addOperator(new EndText(this));
        addOperator(new SetGraphicsStateParameters(this));
        addOperator(new SetFontAndSize(this));
        addOperator(new SetTextRenderingMode(this));
        addOperator(new SetMatrix(this));
        addOperator(new MoveText(this));
        addOperator(new NextLine(this));
        addOperator(new MoveTextSetLeading(this));
        addOperator(new SetCharSpacing(this));
        addOperator(new SetTextLeading(this));
        addOperator(new SetTextRise(this));
        addOperator(new SetWordSpacing(this));
        addOperator(new SetTextHorizontalScaling(this));

        /*
         * Do not use the PDFBox Operator, because of the PageDrawer class cast Or because the Operator doesn't exist
         */

        addOperator(new StubOperator(this, OperatorName.LINE_TO));
        addOperator(new StubOperator(this, OperatorName.APPEND_RECT));
        addOperator(new StubOperator(this, OperatorName.CURVE_TO));
        addOperator(new StubOperator(this, OperatorName.CURVE_TO_REPLICATE_FINAL_POINT));
        addOperator(new StubOperator(this, OperatorName.CURVE_TO_REPLICATE_INITIAL_POINT));
        addOperator(new StubOperator(this, OperatorName.ENDPATH));
        addOperator(new StubOperator(this, OperatorName.BEGIN_INLINE_IMAGE));
        addOperator(new StubOperator(this, OperatorName.BEGIN_INLINE_IMAGE_DATA));
        addOperator(new StubOperator(this, OperatorName.END_INLINE_IMAGE));
        addOperator(new StubOperator(this, OperatorName.MOVE_TO));
        addOperator(new StubOperator(this, OperatorName.CLIP_EVEN_ODD));
        addOperator(new StubOperator(this, OperatorName.CLIP_NON_ZERO));
        addOperator(new StubOperator(this, OperatorName.CLOSE_PATH));

        addOperator(new StubOperator(this, OperatorName.SHOW_TEXT));
        addOperator(new StubOperator(this, OperatorName.SHOW_TEXT_ADJUSTED));
        addOperator(new StubOperator(this, OperatorName.SHOW_TEXT_LINE));
        addOperator(new StubOperator(this, OperatorName.SHOW_TEXT_LINE_AND_SPACE));

        addOperator(new StubOperator(this, OperatorName.CLOSE_FILL_NON_ZERO_AND_STROKE));
        addOperator(new StubOperator(this, OperatorName.FILL_NON_ZERO_AND_STROKE));
        addOperator(new StubOperator(this, OperatorName.CLOSE_FILL_EVEN_ODD_AND_STROKE));
        addOperator(new StubOperator(this, OperatorName.FILL_EVEN_ODD_AND_STROKE));

        addOperator(new StubOperator(this, OperatorName.BEGIN_MARKED_CONTENT_SEQ));
        addOperator(new StubOperator(this, OperatorName.BEGIN_MARKED_CONTENT));
        addOperator(new StubOperator(this, OperatorName.MARKED_CONTENT_POINT_WITH_PROPS));
        addOperator(new StubOperator(this, OperatorName.END_MARKED_CONTENT));
        addOperator(new StubOperator(this, OperatorName.BEGIN_COMPATIBILITY_SECTION));
        addOperator(new StubOperator(this, OperatorName.END_COMPATIBILITY_SECTION));

        addOperator(new StubOperator(this, OperatorName.TYPE3_D0));
        addOperator(new StubOperator(this, OperatorName.TYPE3_D1));

        addOperator(new StubOperator(this, OperatorName.FILL_NON_ZERO));
        addOperator(new StubOperator(this, OperatorName.LEGACY_FILL_NON_ZERO));
        addOperator(new StubOperator(this, OperatorName.FILL_EVEN_ODD));

        addOperator(new StubOperator(this, OperatorName.SET_LINE_MITERLIMIT));
        addOperator(new StubOperator(this, OperatorName.MARKED_CONTENT_POINT));

        addOperator(new StubOperator(this, OperatorName.SET_FLATNESS));

        addOperator(new StubOperator(this, OperatorName.SET_RENDERINGINTENT));
        addOperator(new StubOperator(this, OperatorName.CLOSE_AND_STROKE));
        addOperator(new StubOperator(this, OperatorName.STROKE_PATH));
        addOperator(new StubOperator(this, OperatorName.SHADING_FILL));
    }

    /**
     * Check operands of the "ri" operator. Operands must exist in the RenderingIntent list.
     * (org.apache.pdfbox.preflight.utils.RenderingIntents)
     * 
     * @param operator
     *            the "ri" operator
     * @param arguments
     *            the "ri" operands
     * @throws ContentStreamException
     *             ERROR_GRAPHIC_UNEXPECTED_VALUE_FOR_KEY if the operand is invalid
     */
    protected void validateRenderingIntent(Operator operator, List<COSBase> arguments) throws ContentStreamException
    {
        if (OperatorName.SET_RENDERINGINTENT.equals(operator.getName())
                && arguments.get(0) instanceof COSName
                && !RenderingIntents.contains((COSName) arguments.get(0)))
        {
            registerError("Unexpected value '" + arguments.get(0) + "' for ri operand. ",
                    ERROR_GRAPHIC_UNEXPECTED_VALUE_FOR_KEY);
        }
    }

    /**
     * Valid the number of graphic states if the operator is the Save Graphic state operator ("q")
     * 
     * @param operator
     */
    protected void validateNumberOfGraphicStates(Operator operator)
    {
        if (OperatorName.SAVE.equals(operator.getName()))
        {
            int numberOfGraphicStates = this.getGraphicsStackSize();
            if (numberOfGraphicStates > MAX_GRAPHIC_STATES)
            {
                registerError("Too many graphic states", ERROR_GRAPHIC_TOO_MANY_GRAPHIC_STATES);
            }
        }
    }

    /**
     * Throw a ContentStreamException if the LZW filter is used in a InlinedImage.
     * 
     * @param operator the InlinedImage object (BI to EI)
     */
    protected void validateInlineImageFilter(Operator operator)
    {
        COSDictionary dict = operator.getImageParameters();
        /*
         * Search a Filter declaration in the InlinedImage dictionary. The LZWDecode Filter is forbidden.
         */
        COSBase filter = dict.getDictionaryObject(COSName.F, COSName.FILTER);
        FilterHelper.isAuthorizedFilter(context,
                filter instanceof COSName ? ((COSName) filter).getName() : null);
    }

    /**
     * This method validates if the ColorSpace used by the InlinedImage is consistent with
     * the color space defined in OutputIntent dictionaries.
     * 
     * @param operator the InlinedImage object (BI to EI)
     * @throws IOException
     */
    protected void validateInlineImageColorSpace(Operator operator) throws IOException
    {
        COSDictionary dict = operator.getImageParameters();

        COSBase csInlinedBase = dict.getDictionaryObject(COSName.CS, COSName.COLORSPACE);
        ColorSpaceHelper csHelper = null;
        if (csInlinedBase != null)
        {
            if (csInlinedBase instanceof COSName)
            {
                // In InlinedImage only DeviceGray/RGB/CMYK and restricted Indexed
                // color spaces are allowed.
                String colorSpace = ((COSName) csInlinedBase).getName();
                ColorSpaces cs = null;

                try
                {
                    cs = ColorSpaces.valueOf(colorSpace);
                }
                catch (IllegalArgumentException e)
                {
                    // The color space is unknown. Try to access the resources dictionary,
                    // the color space can be a reference.
                    PDColorSpace pdCS = this.getResources().getColorSpace(COSName.getPDFName(colorSpace));
                    if (pdCS != null)
                    {
                        cs = ColorSpaces.valueOf(pdCS.getName());
                        csHelper = getColorSpaceHelper(pdCS);
                    }
                }

                if (cs == null)
                {
                    registerError("The ColorSpace " + colorSpace + " is unknown", ERROR_GRAPHIC_UNEXPECTED_VALUE_FOR_KEY);
                    return;
                }
            }

            if (csHelper == null)
            {
                // convert to long names first
                csInlinedBase = toLongName(csInlinedBase);
                if (csInlinedBase instanceof COSArray && ((COSArray) csInlinedBase).size() > 1)
                {
                    COSArray srcArray = (COSArray) csInlinedBase;
                    COSBase csType = srcArray.get(0);
                    if (COSName.I.equals(csType) || COSName.INDEXED.equals(csType))
                    {
                        COSArray dstArray = new COSArray();
                        dstArray.addAll(srcArray);
                        dstArray.set(0, COSName.INDEXED);
                        dstArray.set(1, toLongName(srcArray.get(1)));
                        csInlinedBase = dstArray;
                    }
                }                
                PDColorSpace pdCS = PDColorSpace.create(csInlinedBase);
                csHelper = getColorSpaceHelper(pdCS);
            }
            csHelper.validate();
        }
    }

    private ColorSpaceHelper getColorSpaceHelper(PDColorSpace pdCS)
    {
        PreflightConfiguration cfg = context.getConfig();
        ColorSpaceHelperFactory csFact = cfg.getColorSpaceHelperFact();
        return csFact.getColorSpaceHelper(context, pdCS, ColorSpaceRestriction.ONLY_DEVICE);
    }
    
    // deliver the long name of a device colorspace, or the parameter
    private COSBase toLongName(COSBase cs)
    {
        if (COSName.RGB.equals(cs))
        {
            return COSName.DEVICERGB;
        }
        if (COSName.CMYK.equals(cs))
        {
            return COSName.DEVICECMYK;
        }
        if (COSName.G.equals(cs))
        {
            return COSName.DEVICEGRAY;
        }
        return cs;
    }
    
    /**
     * This method validates if the ColorOperator can be used with the color space
     * defined in OutputIntent dictionaries.
     * 
     * @param operation the color operator
     * @throws ContentStreamException
     */
    protected void checkColorOperators(String operation) throws ContentStreamException
    {
        PDColorSpace cs = getColorSpace(operation);

        if ((OperatorName.NON_STROKING_RGB.equals(operation)
                || OperatorName.STROKING_COLOR_RGB.equals(operation))
                && !validColorSpace(cs, ColorSpaceType.RGB))
        {
            registerError("The operator \"" + operation + "\" can't be used with CMYK Profile",
                    ERROR_GRAPHIC_INVALID_COLOR_SPACE_RGB);
            return;
        }
        if ((OperatorName.NON_STROKING_CMYK.equals(operation)
                || OperatorName.STROKING_COLOR_CMYK.equals(operation))
                && !validColorSpace(cs, ColorSpaceType.CMYK))
        {
            registerError("The operator \"" + operation + "\" can't be used with RGB Profile",
                    ERROR_GRAPHIC_INVALID_COLOR_SPACE_CMYK);
            return;
        }
        if ((OperatorName.NON_STROKING_GRAY.equals(operation)
                || OperatorName.STROKING_COLOR_GRAY.equals(operation)
                || OperatorName.FILL_NON_ZERO.equals(operation)
                || OperatorName.LEGACY_FILL_NON_ZERO.equals(operation)
                || OperatorName.FILL_EVEN_ODD.equals(operation)
                || OperatorName.FILL_NON_ZERO_AND_STROKE.equals(operation)
                || OperatorName.FILL_EVEN_ODD_AND_STROKE.equals(operation)
                || OperatorName.CLOSE_FILL_NON_ZERO_AND_STROKE.equals(operation)
                || OperatorName.CLOSE_FILL_EVEN_ODD_AND_STROKE.equals(operation))
                && !validColorSpace(cs, ColorSpaceType.ALL))
        {
            registerError("The operator \"" + operation + "\" can't be used without Color Profile",
                    ERROR_GRAPHIC_INVALID_COLOR_SPACE_MISSING);
        }
    }

    /**
     * In some cases, the colorspace isn't checked because defaults (/DeviceGray) is used. Thus we
     * need to check all text output, stroke and fill for /DeviceGray.
     *
     * @param operator an operator.
     * @throws ContentStreamException
     */
    void validateDefaultColorSpace(Operator operator) throws ContentStreamException
    {
        boolean v = false;
        String op = operator.getName();
        if (OperatorName.SHOW_TEXT.equals(op) || OperatorName.SHOW_TEXT_ADJUSTED.equals(op)
                || OperatorName.SHOW_TEXT_LINE.equals(op)
                || OperatorName.SHOW_TEXT_LINE_AND_SPACE.equals(op))
        {
            RenderingMode rm = getGraphicsState().getTextState().getRenderingMode();
            if (rm.isFill() && 
                    getGraphicsState().getNonStrokingColor().getColorSpace() instanceof PDDeviceGray)
            {
                v = true;
            }
            if (rm.isStroke() && 
                    getGraphicsState().getStrokingColor().getColorSpace() instanceof PDDeviceGray)
            {
                v = true;
            }
        }
        // fills
        if ((OperatorName.FILL_NON_ZERO.equals(op) || OperatorName.LEGACY_FILL_NON_ZERO.equals(op)
                || OperatorName.FILL_EVEN_ODD.equals(op)
                || OperatorName.FILL_NON_ZERO_AND_STROKE.equals(op)
                || OperatorName.FILL_EVEN_ODD_AND_STROKE.equals(op)
                || OperatorName.CLOSE_FILL_NON_ZERO_AND_STROKE.equals(op)
                || OperatorName.CLOSE_FILL_EVEN_ODD_AND_STROKE.equals(op))
                &&
                getGraphicsState().getNonStrokingColor().getColorSpace() instanceof PDDeviceGray)
        {
            v = true;
        }
        // strokes
        if ((OperatorName.FILL_NON_ZERO_AND_STROKE.equals(op)
                || OperatorName.FILL_EVEN_ODD_AND_STROKE.equals(op)
                || OperatorName.CLOSE_FILL_NON_ZERO_AND_STROKE.equals(op)
                || OperatorName.CLOSE_FILL_EVEN_ODD_AND_STROKE.equals(op)
                || OperatorName.CLOSE_AND_STROKE.equals(op) || OperatorName.STROKE_PATH.equals(op))
                &&
                getGraphicsState().getStrokingColor().getColorSpace() instanceof PDDeviceGray)
        {
            v = true;
        }
        if (v && !validColorSpaceDestOutputProfile(PreflightStreamEngine.ColorSpaceType.ALL))
        {
            registerError("/DeviceGray default for operator \"" + op
                    + "\" can't be used without Color Profile",
                    ERROR_GRAPHIC_INVALID_COLOR_SPACE_MISSING);
        }
    }

    private boolean validColorSpace(PDColorSpace colorSpace, ColorSpaceType expectedIccType)
            throws ContentStreamException
    {
        if (colorSpace == null)
        {
            return validColorSpaceDestOutputProfile(expectedIccType);
        }
        else
        {
            return isDeviceIndependent(colorSpace, expectedIccType) ||
                   validColorSpaceDestOutputProfile(expectedIccType);
        }
    }

    /*
     * Check if the ColorProfile provided by the DestOutputProfile entry isn't null and
     * if the ColorSpace represented by the Profile has the right type (RGB or CMYK)
     * 
     * @param expectedType
     * @return
     */
    private boolean validColorSpaceDestOutputProfile(ColorSpaceType expectedType)
            throws ContentStreamException
    {
        try
        {
            ICCProfileWrapper profileWrapper = ICCProfileWrapper.getOrSearchICCProfile(context);
            if (profileWrapper == null)
            {
                return false;
            }
            switch (expectedType)
            {
                case RGB:
                    return profileWrapper.isRGBColorSpace();
                case CMYK:
                    return profileWrapper.isCMYKColorSpace();
                default:
                    return true;
            }

        }
        catch (ValidationException e)
        {
            throw new ContentStreamException(e);
        }
    }

    /*
     * Return true if the given ColorSpace is an independent device ColorSpace.
     * If the color space is an ICCBased, check the embedded profile color (RGB or CMYK)
     */
    private boolean isDeviceIndependent(PDColorSpace cs, ColorSpaceType expectedIccType)
    {
        if (cs instanceof PDICCBased)
        {
            int type = ((PDICCBased)cs).getColorSpaceType();
            switch (expectedIccType)
            {
                case RGB: return type == ColorSpace.TYPE_RGB;
                case CMYK: return type == ColorSpace.TYPE_CMYK;
                default: return true;
            }
        }
        else if (cs instanceof PDSeparation)
        {
            return isDeviceIndependent(((PDSeparation)cs).getAlternateColorSpace(),
                    expectedIccType);
        }
        else
        {
            return cs instanceof PDCIEBasedColorSpace;
        }
    }

    /*
     * Return the current color space used by the operation
     */
    private PDColorSpace getColorSpace(String operation)
    {
        if (getGraphicsState() == null)
        {
            return null;
        }

        if (operation.equals("rg") || operation.equals("g") || operation.equals("k") ||
            operation.equals("f") || operation.equals("F") || operation.equals("f*"))
        {
            // non stroking operator
            return getGraphicsState().getNonStrokingColorSpace();
        }
        else
        {
            // stroking operator
            return getGraphicsState().getStrokingColorSpace();
        }
    }

    /**
     * This method validates if the ColorSpace used as operand is consistent with
     * the color space defined in OutputIntent dictionaries.
     * 
     * @param operator
     * @param arguments
     * @throws IOException
     */
    protected void checkSetColorSpaceOperators(Operator operator, List<COSBase> arguments) throws IOException
    {
        if (!OperatorName.STROKING_COLORSPACE.equals(operator.getName())
                && !OperatorName.NON_STROKING_COLORSPACE.equals(operator.getName()))
        {
            return;
        }

        String colorSpaceName;
        if (arguments.get(0) instanceof COSString)
        {
            colorSpaceName = (arguments.get(0)).toString();
        }
        else if (arguments.get(0) instanceof COSName)
        {
            colorSpaceName = ((COSName) arguments.get(0)).getName();
        }
        else
        {
            registerError("The operand " + arguments.get(0) + " for colorSpace operator " + 
                    operator.getName() + " doesn't have the expected type", 
                    ERROR_GRAPHIC_UNEXPECTED_VALUE_FOR_KEY);
            return;
        }

        ColorSpaceHelper csHelper = null;
        ColorSpaces cs = null;
        try
        {
            cs = ColorSpaces.valueOf(colorSpaceName);
        }
        catch (IllegalArgumentException e)
        {
            /*
             * The color space is unknown. Try to access the resources dictionary, the color space can be a reference.
             */
            PDColorSpace pdCS = this.getResources().getColorSpace(COSName.getPDFName(colorSpaceName));
            if (pdCS != null)
            {
                cs = ColorSpaces.valueOf(pdCS.getName());
                PreflightConfiguration cfg = context.getConfig();
                ColorSpaceHelperFactory csFact = cfg.getColorSpaceHelperFact();
                csHelper = csFact.getColorSpaceHelper(context, pdCS, ColorSpaceRestriction.NO_RESTRICTION);
            }
        }

        if (cs == null)
        {
            registerError("The ColorSpace " + colorSpaceName + " is unknown", ERROR_GRAPHIC_UNEXPECTED_VALUE_FOR_KEY);
            return;
        }

        if (csHelper == null)
        {
            PDColorSpace pdCS = PDColorSpace.create(COSName.getPDFName(colorSpaceName));
            PreflightConfiguration cfg = context.getConfig();
            ColorSpaceHelperFactory csFact = cfg.getColorSpaceHelperFact();
            csHelper = csFact.getColorSpaceHelper(context, pdCS, ColorSpaceRestriction.NO_RESTRICTION);
        }

        csHelper.validate();
    }

    /**
     * Add a validation error into the PreflightContext
     * 
     * @param msg
     *            exception details
     * @param errorCode
     *            the error code.
     */
    protected void registerError(String msg, String errorCode)
    {
        registerError(msg, errorCode, null);
    }

    public void registerError(String msg, String errorCode, Throwable cause)
    {
        registerError(msg, errorCode, false, cause);
    }
    
    protected void registerError(String msg, String errorCode, boolean warning)
    {
        registerError(msg, errorCode, warning, null);
    }

    public void registerError(String msg, String errorCode, boolean warning,
            Throwable cause)
    {
        ValidationError error = new ValidationError(errorCode, msg, cause);
        error.setWarning(warning);
        this.context.addValidationError(error);
    }
}
