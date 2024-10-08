
/*
 * json-patch - A command-line tool for modifying JSON documents
 *
 * Copyright (c) 2023, Arno Unkrig
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of conditions and the
 *       following disclaimer.
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 *       following disclaimer in the documentation and/or other materials provided with the distribution.
 *    3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote
 *       products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package de.unkrig.jsonpatch;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import de.unkrig.commons.file.contentstransformation.ContentsTransformer;
import de.unkrig.commons.file.filetransformation.FileContentsTransformer;
import de.unkrig.commons.file.filetransformation.FileTransformer;
import de.unkrig.commons.io.OutputStreams;
import de.unkrig.commons.lang.AssertionUtil;
import de.unkrig.commons.lang.ExceptionUtil;
import de.unkrig.commons.lang.protocol.Transformer;

public
class JsonPatch {

    static {
        AssertionUtil.enableAssertionsForThisClass();
    }

    private final GsonBuilder                                 gsonBuilder = new GsonBuilder();
    private final List<Transformer<JsonElement, JsonElement>> documentModifiers = new ArrayList<>();

    /**
     * If <var>value</var> is {@code true}, then wrap and indent json objects and arrays. Default is {@code false}.
     */
    public GsonBuilder
    getGsonBuilder() { return this.gsonBuilder; }

    /**
     * @see #set(JsonElement, String, JsonElement, SetMode)
     */
    public void
    addSet(String spec, JsonElement value, SetMode mode) throws IOException {
        this.documentModifiers.add(root -> this.set(root, spec, value, mode));
    }
    public static enum SetMode { ANY, EXISTING, NON_EXISTING }

    /**
     * @see #remove(JsonElement, String, RemoveMode)
     */
    public void
    addRemove(String spec, RemoveMode mode) {
        this.documentModifiers.add(root -> this.remove(root, spec, mode));
    }
    public static enum RemoveMode { ANY, EXISTING }

    /**
     * @see #insert(JsonElement, String, JsonElement)
     */
    public void
    addInsert(String spec, JsonElement value) {
        this.documentModifiers.add(root -> this.insert(root, spec, value));
    }

    /**
     * Adds or changes one array element or object member.
     * 
     * @throws SpecMatchException <var>mode</var> is {@code EXISTING}, and the specified array index is out of range
     * @throws SpecMatchException <var>mode</var> is {@code NON_EXISTING}, and the specified array index does not
     *                            equal the array size
     * @throws SpecMatchException <var>mode</var> is {@code EXISTING}, and the specified object member does not exist
     * @throws SpecMatchException <var>mode</var> is {@code NON_EXISTING}, and the specified object member does exist
     */
    public JsonElement
    set(JsonElement root, String spec, JsonElement value, SetMode mode) {

        /**
         * Empty spec means "replace the entire document with the value".
         */
        if (spec.isEmpty()) return value;

        JsonPatch.processSpec(root, spec, new SpecHandler() {

            public void
            handleObjectMember(JsonObject jsonObject, String memberName) {
                switch (mode) {
                case ANY:
                    break;
                case EXISTING:
                    if (!jsonObject.has(memberName)) throw new SpecMatchException("Member \"" + memberName + "\" does not exist");
                    break;
                case NON_EXISTING:
                    if (jsonObject.has(memberName)) throw new SpecMatchException("Member \"" + memberName + "\" already exists");
                    break;
                }
                jsonObject.add(memberName, value);
            }

            public void
            handleArrayElement(JsonArray jsonArray, int index) {
                switch (mode) {
                case ANY:
                    break;
                case EXISTING:
                    if (index < 0 || index >= jsonArray.size()) throw new SpecMatchException("Array index " + index + " is out of range");
                    break;
                case NON_EXISTING:
                    if (index != jsonArray.size()) throw new SpecMatchException("Index " + index + " not equal to array size");
                    break;
                }
                if (index == jsonArray.size()) {
                    jsonArray.add(value);
                } else {
                    jsonArray.set(index, value);
                }
            }
        });
        
        return root;
    }

    /**
     * Removes one array element or object member.
     * 
     * @param mode                (Irrelevant if an array element is specified)
     * @throws SpecMatchException The specified array index is out of range (-arraySize ... arraySize-1)
     * @throws SpecMatchException <var>mode</var> is {@code EXISTING}, and the specified object member does not exist
     */
    public JsonElement
    remove(JsonElement root, String spec, RemoveMode mode) {
        
        JsonPatch.processSpec(root, spec, new SpecHandler() {
            
            public void
            handleObjectMember(JsonObject jsonObject, String memberName) {

                JsonElement prev = jsonObject.remove(memberName);

                if (prev == null && mode == RemoveMode.EXISTING) {
                    throw new SpecMatchException("Member \"" + memberName + "\" does not exist");
                }
            }
            
            public void
            handleArrayElement(JsonArray jsonArray, int index) {
                if (index < 0 || index >= jsonArray.size()) throw new SpecMatchException("Array index " + index + " is out of range");
                jsonArray.remove(index);
            }
        });

        return root;
    }
    
    /**
     * Inserts an element into an array.
     *
     * @throws SpecMatchException The specified array index is out of range (-arraySize ... arraySize)
     * @throws SpecMatchException The <var>spec</var> specified an object member (and not an array element)
     */
    public JsonElement
    insert(JsonElement root, String spec, JsonElement value) {

        JsonPatch.processSpec(root, spec, new SpecHandler() {

            public void
            handleObjectMember(JsonObject jsonObject, String memberName) {
                throw new SpecMatchException("Cannot insert into object; use SET instead");
            }

            public void
            handleArrayElement(JsonArray jsonArray, int index) {

                // "JsonArray" has no "insert()" method; thus temporarily remove all elements at and after the index,
                // then add the value, then add the previously removed elements.
                if (index < 0 || index > jsonArray.size()) throw new SpecMatchException("Array index " + index + " is out of range");
                List<JsonElement> tmp = new ArrayList<>();
                while (jsonArray.size() > index) tmp.add(jsonArray.remove(index));
                jsonArray.add(value);
                for (JsonElement element : tmp) jsonArray.add(element);
            }
        });
        return root;
    }

    public
    interface SpecHandler {
        void handleObjectMember(JsonObject jsonObject, String memberName);
        void handleArrayElement(JsonArray jsonArray, int index);
    }

    private static final Pattern OBJECT_MEMBER_SPEC1  = Pattern.compile("\\.([A-Za-z0-9_]+)$");
    private static final Pattern OBJECT_MEMBER_SPEC2  = Pattern.compile("\\.([A-Za-z0-9_]+)");
    private static final Pattern ARRAY_ELEMENT_SPEC1  = Pattern.compile("\\[]$");
    private static final Pattern ARRAY_ELEMENT_SPEC2  = Pattern.compile("\\[(-?\\d+)]$");
    private static final Pattern ARRAY_ELEMENT_SPEC3  = Pattern.compile("\\[(-?\\d+)]");

    private static void
    processSpec(JsonElement root, String spec, SpecHandler specHandler) {

        JsonElement el = root;
        for (String s = spec;;) {
            try {
                Matcher m;
                
                if ((m = OBJECT_MEMBER_SPEC1.matcher(s)).lookingAt()) {  // .abc (last component)
                    JsonObject jsonObject = el.getAsJsonObject();
                    String     memberName = m.group(1);
                    
                    try {
                        specHandler.handleObjectMember(jsonObject, memberName);
                    } catch (RuntimeException e) {
                        throw ExceptionUtil.wrap("Processing object " + jsonObject, e);
                    }
                    return;
                } else
                if ((m = OBJECT_MEMBER_SPEC2.matcher(s)).lookingAt()) {  // .abc (any but the last component)
                    JsonObject jsonObject = el.getAsJsonObject();
                    String     memberName = m.group(1);
    
                    el = jsonObject.get(memberName);
                    s = s.substring(m.end());
                } else
                if ((m = ARRAY_ELEMENT_SPEC1.matcher(s)).lookingAt()) {  // [] (last component)
                    JsonArray jsonArray = el.getAsJsonArray();
                    try {
                        specHandler.handleArrayElement(jsonArray, jsonArray.size());
                    } catch (RuntimeException e) {
                        throw ExceptionUtil.wrap("Processing array " + jsonArray, e);
                    }
                    return;
                } else
                if ((m = ARRAY_ELEMENT_SPEC2.matcher(s)).lookingAt()) {  // [7] (last component)
                    JsonArray jsonArray = el.getAsJsonArray();
                    int       index     = Integer.parseInt(m.group(1));
                    if (index < 0) index += jsonArray.size();
                    try {
                        specHandler.handleArrayElement(jsonArray, index);
                    } catch (RuntimeException e) {
                        throw ExceptionUtil.wrap("Processing array " + jsonArray, e);
                    }
                    return;
                } else
                if ((m = ARRAY_ELEMENT_SPEC3.matcher(s)).lookingAt()) {  // [7] (any but the last component)
                    JsonArray jsonArray = el.getAsJsonArray();
                    int index = Integer.parseInt(m.group(1));
                    if (index < 0) index += jsonArray.size();
    
                    el = jsonArray.get(index);
                    s = s.substring(m.end());
                } else
                {
                    throw new SpecSyntaxException("Invalid spec \"" + s + "\"");
                }
            } catch (RuntimeException e) {
                throw ExceptionUtil.wrap(
                    "Processing spec \"" + spec + "\" at offset " + (spec.length() - s.length()),
                    e
                );
            }
        }
    }

    public void
    transform(Reader in, OutputStream out, Charset outCharset) throws IOException {

        Gson gson = this.gsonBuilder.create();

        // Read the document from the reader.
        JsonReader jsonReader = gson.newJsonReader(in);
        JsonElement e = JsonParser.parseReader(jsonReader);

        // Apply all configured "document modifiers".
        for (Transformer<JsonElement, JsonElement> dm : JsonPatch.this.documentModifiers) e = dm.transform(e);

        // Write the document to the output stream.
        try (Writer w = new OutputStreamWriter(OutputStreams.unclosable(out), outCharset)) {
            w.write(gson.toJson(e));
        }
    }

    public ContentsTransformer
    contentsTransformer(Charset inCharset, Charset outCharset) {

        return new ContentsTransformer() {
            
            @Override public void
            transform(String path, InputStream is, OutputStream os) throws IOException {

                InputStreamReader r = new InputStreamReader(is, inCharset);
                
                JsonPatch.this.transform(r, os, outCharset);
            }
        };
    }

    public FileTransformer
    fileTransformer(Charset inCharset, Charset outCharset, boolean keepOriginals) {
        return new FileContentsTransformer(this.contentsTransformer(inCharset, outCharset), keepOriginals);
    }

    /**
     * Iff <var>jsonDocumentOrFile</var> does not start with {@code "@"}, parse it as a JSON document and return that.
     * <p>
     *   Iff <var>jsonDocumentOrFile</var> does start with {@code "@"}, take the letters following the "@" as a file
     *   name, open that file for reading, parse its contents as a JSON document and return that.
     * </p>
     */
    public static JsonElement
    jsonDocumentOrFile(String jsonDocumentOrFile, Charset fileCharset) throws IOException, FileNotFoundException {
        
        try (Reader r = JsonPatch.stringOrFileReader(jsonDocumentOrFile, fileCharset)) {
            return parseJson(r);
        }
    }

    /**
     * Iff <var>documentOrFile</var> equals {@code "-"}, return a {@link Reader} for {@code System.out}.
     * <p>
     *   Iff <var>documentOrFile</var> start with {@code "@"}, take the letters following the "@" as a file
     *   name, open that file for reading, and return a {@link FileReader} with the given {@link Charset}.
     * </p>
     * <p>
     *   Otherwise, return a {@link StringReader} on the <var>documentOrFile</var> string.
     * </p>
     * 
     * @param fileCharset Used iff <var>documentOrFile</var> starts with {@code "@"}
     */
    private static Reader
    stringOrFileReader(String documentOrFile, Charset fileCharset) throws FileNotFoundException {
        return (
        	"-".equals(documentOrFile)
        	? new InputStreamReader(System.in) // "System.in" is platform-default-encoded.
            : documentOrFile.startsWith("@")
            ? new InputStreamReader(new FileInputStream(documentOrFile.substring(1)), fileCharset)
    		: new StringReader(documentOrFile)
        );
    }

    private static JsonElement
    parseJson(Reader r) {
        JsonReader jsonReader = new JsonReader(r);
        jsonReader.setLenient(true);
        return JsonParser.parseReader(jsonReader);
    }
}
