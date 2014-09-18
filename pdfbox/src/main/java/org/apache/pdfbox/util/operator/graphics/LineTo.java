/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pdfbox.util.operator.graphics;

import java.io.IOException;
import java.util.List;
import java.awt.geom.Point2D;

import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.util.operator.Operator;

/**
 * l Append straight line segment to path.
 *
 * @author Ben Litchfield
 */
public class LineTo extends GraphicsOperatorProcessor
{
    @Override
    public void process(Operator operator, List<COSBase> operands) throws IOException
    {
        // append straight line segment from the current point to the point
        COSNumber x = (COSNumber)operands.get(0);
        COSNumber y = (COSNumber)operands.get(1);

        Point2D pos = context.transformedPoint(x.doubleValue(), y.doubleValue());
        context.lineTo((float) pos.getX(), (float) pos.getY());
    }

    @Override
    public String getName()
    {
        return "l";
    }
}
