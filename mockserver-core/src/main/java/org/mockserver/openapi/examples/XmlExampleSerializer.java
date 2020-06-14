/*
 *  Copyright 2017 SmartBear Software
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.mockserver.openapi.examples;

import org.mockserver.openapi.examples.models.ArrayExample;
import org.mockserver.openapi.examples.models.Example;
import org.mockserver.openapi.examples.models.ObjectExample;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;

public class XmlExampleSerializer {
    int depth = 0;

    public String serialize(Example o) {
        XMLStreamWriter writer = null;
        try {
            XMLOutputFactory f = XMLOutputFactory.newFactory();
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            writer = f.createXMLStreamWriter(out);

            writer.writeStartDocument("UTF-8", "1.1");
            writeTo(writer, o);
            writer.close();
            return out.toString();
        } catch (XMLStreamException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void writeTo(XMLStreamWriter writer, Example o) throws XMLStreamException {
        depth += 1;
        if (o instanceof ObjectExample) {
            ObjectExample or = (ObjectExample) o;
            String name = o.getName();
            if (depth == 1 && name == null) {
                // write primitive type container
                name = getTypeName(o);
            }
            if (name == null) {
                name = "AnonymousModel";
            }

            if (o.getNamespace() != null) {
                if (o.getPrefix() != null) {
                    writer.writeStartElement(o.getPrefix(), name, o.getNamespace());
                } else {
                    writer.writeStartElement(o.getNamespace(), name);
                }
            } else {
                writer.writeStartElement(name);
            }

            for (String key : or.keySet()) {
                Object obj = or.get(key);
                if (obj instanceof Example) {
                    Example example = (Example) obj;
                    if (example.getName() == null) {
                        example.setName(key);
                    }

                    writeTo(writer, (Example) obj);
                }
            }
            writer.writeEndElement();
        } else if (o instanceof ArrayExample) {
            ArrayExample ar = (ArrayExample) o;
            if (o.getWrapped() != null && o.getWrapped()) {
                if (o.getWrappedName() != null) {
                    if (o.getNamespace() != null) {
                        if (o.getPrefix() != null) {
                            writer.writeStartElement(o.getPrefix(), o.getWrappedName(), o.getNamespace());
                        } else {
                            writer.writeStartElement(o.getNamespace(), o.getWrappedName());
                        }
                    } else {
                        writer.writeStartElement(o.getWrappedName());
                    }

                } else {
                    if (o.getNamespace() != null) {
                        if (o.getPrefix() != null) {
                            writer.writeStartElement(o.getPrefix(), o.getName() + "s", o.getNamespace());
                        } else {
                            writer.writeStartElement(o.getNamespace(), o.getName() + "s");
                        }
                    } else {
                        writer.writeStartElement(o.getName() + "s");
                    }
                }
            }
            for (Example item : ar.getItems()) {
                if (item.getName() == null) {

                    String name = o.getName();
                    if (name == null) {
                        name = item.getTypeName();
                    }

                    if (o.getNamespace() != null) {
                        if (o.getPrefix() != null) {
                            writer.writeStartElement(o.getPrefix(), name, o.getNamespace());
                        } else {
                            writer.writeStartElement(o.getNamespace(), name);
                        }
                    } else {
                        writer.writeStartElement(name);
                    }
                }
                writeTo(writer, item);
                if (item.getName() == null && o.getName() != null) {
                    writer.writeEndElement();
                }
            }
            if (o.getWrapped() != null && o.getWrapped()) {
                writer.writeEndElement();
            }
        } else {
            String name = o.getName();
            if (depth == 1 && name == null) {
                // write primitive type container
                name = getTypeName(o);
            }
            if (o.getAttribute() != null && o.getAttribute()) {

                if (o.getNamespace() != null) {
                    if (o.getPrefix() != null) {
                        writer.writeAttribute(o.getPrefix(), o.getNamespace(), name, o.asString());
                    } else {
                        writer.writeAttribute(o.getNamespace(), name, o.asString());
                    }
                } else {
                    writer.writeAttribute(name, o.asString());
                }
            } else if (name == null) {
                writer.writeCharacters(o.asString());
            } else {
                if (o.getNamespace() != null) {
                    if (o.getPrefix() != null) {
                        writer.writeStartElement(o.getPrefix(), name, o.getNamespace());
                    } else {
                        writer.writeStartElement(o.getNamespace(), name);
                    }
                } else {
                    writer.writeStartElement(name);
                }

                writer.writeCharacters(o.asString());
                writer.writeEndElement();
            }
        }
    }

    public String getTypeName(Example o) {
        return o.getTypeName();
    }
}
