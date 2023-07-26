
/*
 * de.unkrig.patch - An enhanced version of the UNIX PATCH utility
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

package de.unkrig.zz.jsonpatch.test;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;

import org.junit.Assert;
import org.junit.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import de.unkrig.zz.jsonpatch.JsonPatch;
import de.unkrig.zz.jsonpatch.JsonPatch.RemoveMode;
import de.unkrig.zz.jsonpatch.JsonPatch.SetMode;

/**
 * Test cases for {@link JsonPatch}.
 */
public
class JsonPatchTests {

    // NOP
    @Test public void
    test1() throws Exception {
        assertJsonPatchEquals(new JsonPatch(), "{\"a\":\"b\"}", "{ \"a\": \"b\" }");
    }

    // Simple setting of object members.

    @Test public void
    testSetObjectMember1() throws Exception {
        JsonPatch jsonPatch = new JsonPatch();
        jsonPatch.addSet(".c", jsonDocument("\"d\""), SetMode.ANY);
        assertJsonPatchEquals(jsonPatch, "{\"a\":\"b\",\"c\":\"d\"}", "{ \"a\": \"b\" }");
    }

    @Test public void
    testSetObjectMember2() throws Exception {
        JsonPatch jsonPatch = new JsonPatch();
        jsonPatch.addSet(".c", jsonDocument("[ 1, { 2: 3 } ]"), SetMode.ANY);
        assertJsonPatchEquals(jsonPatch, "{\"a\":\"b\",\"c\":[1,{\"2\":3}]}", "{ \"a\": \"b\" }");
    }

    // Setting an existing object member.

    @Test public void
    testSetExistingObjectMember1() throws Exception {
        JsonPatch jsonPatch = new JsonPatch();
        jsonPatch.addSet(".a", jsonDocument("\"B\""), SetMode.EXISTING);
        assertJsonPatchEquals(jsonPatch, "{\"a\":\"B\"}", "{ \"a\": \"b\" }");
    }
    
    @Test(expected = IllegalArgumentException.class) public void
    testSetExistingObjectMember2() throws Exception {
        JsonPatch jsonPatch = new JsonPatch();
        jsonPatch.addSet(".c", jsonDocument("\"B\""), SetMode.EXISTING);
        assertJsonPatchEquals(jsonPatch, "{\"a\":\"B\"}", "{ \"a\": \"b\" }");
    }

    // Setting a non-existent object member.

    @Test(expected = IllegalArgumentException.class) public void
    testSetNonExistingObjectMember1() throws Exception {
        JsonPatch jsonPatch = new JsonPatch();
        jsonPatch.addSet(".a", jsonDocument("\"B\""), SetMode.NON_EXISTING);
        assertJsonPatchEquals(jsonPatch, "{\"a\":\"b\",\"c\":\"d\"}", "{ \"a\": \"b\" }");
    }
    
    @Test public void
    testSetNonExistingObjectMember2() throws Exception {
        JsonPatch jsonPatch = new JsonPatch();
        jsonPatch.addSet(".c", jsonDocument("\"d\""), SetMode.NON_EXISTING);
        assertJsonPatchEquals(jsonPatch, "{\"a\":\"b\",\"c\":\"d\"}", "{ \"a\": \"b\" }");
    }

    // Simple setting of array elements.

    @Test public void
    testSetArrayElement1() throws Exception {
        JsonPatch jsonPatch = new JsonPatch();
        jsonPatch.addSet("[]", jsonDocument("4"), SetMode.ANY);
        assertJsonPatchEquals(jsonPatch, "[1,2,3,4]", "[ 1, 2, 3 ]");
    }

    @Test public void
    testSetArrayElement2() throws Exception {
        JsonPatch jsonPatch = new JsonPatch();
        jsonPatch.addSet("[1]", jsonDocument("22"), SetMode.ANY);
        assertJsonPatchEquals(jsonPatch, "[1,22,3]", "[ 1, 2, 3 ]");
    }

    @Test public void
    testSetArrayElement3() throws Exception {
        JsonPatch jsonPatch = new JsonPatch();
        jsonPatch.addSet("[-2]", jsonDocument("22"), SetMode.ANY);
        assertJsonPatchEquals(jsonPatch, "[1,22,3]", "[ 1, 2, 3 ]");
    }
    
    @Test public void
    testSetArrayElement4() throws Exception {
        JsonPatch jsonPatch = new JsonPatch();
        jsonPatch.addSet("[3]", jsonDocument("4"), SetMode.ANY);
        assertJsonPatchEquals(jsonPatch, "[1,2,3,4]", "[ 1, 2, 3 ]");
    }

    // Setting an existing array element.

    @Test(expected = IndexOutOfBoundsException.class) public void
    testSetExistingArrayElement1() throws Exception {
        JsonPatch jsonPatch = new JsonPatch();
        jsonPatch.addSet("[]", jsonDocument("4"), SetMode.EXISTING);
        jsonPatch(jsonPatch, "[ 1, 2, 3 ]");
    }
    
    @Test public void
    testSetExistingArrayElement2() throws Exception {
        JsonPatch jsonPatch = new JsonPatch();
        jsonPatch.addSet("[1]", jsonDocument("22"), SetMode.EXISTING);
        assertJsonPatchEquals(jsonPatch, "[1,22,3]", "[ 1, 2, 3 ]");
    }

    @Test public void
    testSetExistingArrayElement3() throws Exception {
        JsonPatch jsonPatch = new JsonPatch();
        jsonPatch.addSet("[-2]", jsonDocument("22"), SetMode.EXISTING);
        assertJsonPatchEquals(jsonPatch, "[1,22,3]", "[ 1, 2, 3 ]");
    }
    
    @Test(expected = IndexOutOfBoundsException.class) public void
    testSetExistingArrayElement4() throws Exception {
        JsonPatch jsonPatch = new JsonPatch();
        jsonPatch.addSet("[3]", jsonDocument("4"), SetMode.EXISTING);
        jsonPatch(jsonPatch, "[ 1, 2, 3 ]");
    }

    // Setting a non-existing array element.
    
    @Test public void
    testSetNonExistingArrayElement1() throws Exception {
        JsonPatch jsonPatch = new JsonPatch();
        jsonPatch.addSet("[]", jsonDocument("4"), SetMode.NON_EXISTING);
        assertJsonPatchEquals(jsonPatch, "[1,2,3,4]", "[ 1, 2, 3 ]");
    }
    
    @Test(expected = IndexOutOfBoundsException.class) public void
    testSetNonExistingArrayElement2() throws Exception {
        JsonPatch jsonPatch = new JsonPatch();
        jsonPatch.addSet("[1]", jsonDocument("22"), SetMode.NON_EXISTING);
        jsonPatch(jsonPatch, "[ 1, 2, 3 ]");
    }
    
    @Test(expected = IndexOutOfBoundsException.class) public void
    testSetNonExistingArrayElement3() throws Exception {
        JsonPatch jsonPatch = new JsonPatch();
        jsonPatch.addSet("[-2]", jsonDocument("22"), SetMode.NON_EXISTING);
        jsonPatch(jsonPatch, "[ 1, 2, 3 ]");
    }
    
    @Test public void
    testSetNonExistingArrayElement4() throws Exception {
        JsonPatch jsonPatch = new JsonPatch();
        jsonPatch.addSet("[3]", jsonDocument("4"), SetMode.NON_EXISTING);
        assertJsonPatchEquals(jsonPatch, "[1,2,3,4]", "[ 1, 2, 3 ]");
    }

    // Inserting an array element.

    @Test public void
    testInsertArrayElement1() throws Exception {
        JsonPatch jsonPatch = new JsonPatch();
        jsonPatch.addInsert("[1]", jsonDocument("99"));
        assertJsonPatchEquals(jsonPatch, "[1,99,2,3]", "[ 1, 2, 3 ]");
    }

    @Test public void
    testInsertArrayElement2() throws Exception {
        JsonPatch jsonPatch = new JsonPatch();
        jsonPatch.addInsert("[-2]", jsonDocument("99"));
        assertJsonPatchEquals(jsonPatch, "[1,99,2,3]", "[ 1, 2, 3 ]");
    }
    
    @Test public void
    testInsertArrayElement3() throws Exception {
        JsonPatch jsonPatch = new JsonPatch();
        jsonPatch.addInsert("[]", jsonDocument("99"));
        assertJsonPatchEquals(jsonPatch, "[1,2,3,99]", "[ 1, 2, 3 ]");
    }
    
    @Test public void
    testInsertArrayElement4() throws Exception {
        JsonPatch jsonPatch = new JsonPatch();
        jsonPatch.addInsert("[3]", jsonDocument("99"));
        assertJsonPatchEquals(jsonPatch, "[1,2,3,99]", "[ 1, 2, 3 ]");
    }
    
    @Test(expected = IndexOutOfBoundsException.class) public void
    testInsertArrayElement5() throws Exception {
        JsonPatch jsonPatch = new JsonPatch();
        jsonPatch.addInsert("[4]", jsonDocument("99"));
        jsonPatch(jsonPatch, "[ 1, 2, 3 ]");
    }
    
    // Removing an object member.

    @Test public void
    testRemoveObjectMember1() throws Exception {
        JsonPatch jsonPatch = new JsonPatch();
        jsonPatch.addRemove(".b", RemoveMode.ANY);
        assertJsonPatchEquals(jsonPatch, "{\"a\":1,\"c\":3}", "{\"a\":1,\"b\":2,\"c\":3}");
    }
    
    @Test public void
    testRemoveObjectMember2() throws Exception {
        JsonPatch jsonPatch = new JsonPatch();
        jsonPatch.addRemove(".d", RemoveMode.ANY);
        assertJsonPatchEquals(jsonPatch, "{\"a\":1,\"b\":2,\"c\":3}", "{\"a\":1,\"b\":2,\"c\":3}");
    }
    
    @Test(expected = IllegalArgumentException.class) public void
    testRemoveObjectMember3() throws Exception {
        JsonPatch jsonPatch = new JsonPatch();
        jsonPatch.addRemove(".d", RemoveMode.EXISTING);
        jsonPatch(jsonPatch, "{\"a\":1,\"b\":2,\"c\":3}");
    }

    // Removing an array element.

    @Test public void
    testRemoveArrayElement1() throws Exception {
        JsonPatch jsonPatch = new JsonPatch();
        jsonPatch.addRemove("[1]", RemoveMode.ANY);
        assertJsonPatchEquals(jsonPatch, "[1,3]", "[ 1, 2, 3 ]");
    }

    @Test public void
    testRemoveArrayElement2() throws Exception {
        JsonPatch jsonPatch = new JsonPatch();
        jsonPatch.addRemove("[-2]", RemoveMode.ANY);
        assertJsonPatchEquals(jsonPatch, "[1,3]", "[ 1, 2, 3 ]");
    }
    
    @Test(expected = IndexOutOfBoundsException.class) public void
    testRemoveArrayElement3() throws Exception {
        JsonPatch jsonPatch = new JsonPatch();
        jsonPatch.addRemove("[-4]", RemoveMode.ANY);
        jsonPatch(jsonPatch, "[ 1, 2, 3 ]");
    }

    // ====================================== Helpers ======================================

    private void
    assertJsonPatchEquals(JsonPatch jsonPatch, String expected, String in) throws IOException {
        Assert.assertEquals(expected, jsonPatch(jsonPatch, in));
    }

    /**
     * Parses a JSON document (<var>in</var>), transforms it with <var>jsonPatch</var>, converts it into a string, and
     * returns it.
     */
    private String
    jsonPatch(JsonPatch jsonPatch, String in) throws IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        jsonPatch.transform(new StringReader(in), baos);
        return baos.toString();
    }

    /**
     * Parses a JSON document (<var>in</var>) and returns it.
     */
    private static JsonElement
    jsonDocument(String in) throws IOException, FileNotFoundException {

        try (JsonReader jsonReader = new JsonReader(new StringReader(in))) {
            jsonReader.setLenient(true);
            return JsonParser.parseReader(jsonReader);
        }
    }
}
