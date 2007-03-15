package org.apache.maven.archiva.repository.consumer;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.archiva.common.ArchivaException;
import org.apache.maven.archiva.common.utils.BaseFile;

/**
 * ConsumerException - details about the failure of a consumer.
 *
 * @author <a href="mailto:joakim@erdfelt.com">Joakim Erdfelt</a>
 * @version $Id$
 */
public class ConsumerException
    extends ArchivaException
{
    private BaseFile file;

    public ConsumerException( BaseFile file, String message, Throwable cause )
    {
        super( message, cause );
        this.file = file;
    }

    public ConsumerException( BaseFile file, String message )
    {
        super( message );
        this.file = file;
    }

    public BaseFile getFile()
    {
        return file;
    }
}
