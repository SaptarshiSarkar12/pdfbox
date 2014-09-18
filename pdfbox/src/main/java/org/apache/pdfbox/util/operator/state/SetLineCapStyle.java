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
package org.apache.pdfbox.util.operator.state;

import java.util.List;

import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.util.operator.Operator;
import org.apache.pdfbox.util.operator.OperatorProcessor;

import java.io.IOException;

/**
 * J: Set the line cap style.
 *
 * @author Andreas Lehmkühler
 */
public class SetLineCapStyle extends OperatorProcessor
{
    @Override
    public void process(Operator operator, List<COSBase> arguments) throws IOException
    {
        int lineCapStyle = ((COSNumber)arguments.get( 0 )).intValue();
        context.getGraphicsState().setLineCap( lineCapStyle );
    }

    @Override
    public String getName()
    {
        return "J";
    }
}
