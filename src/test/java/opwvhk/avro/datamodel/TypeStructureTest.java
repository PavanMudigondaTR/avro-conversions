package opwvhk.avro.datamodel;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import org.apache.avro.JsonProperties;
import org.apache.avro.Schema;
import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Month.MARCH;
import static java.time.ZoneOffset.UTC;
import static opwvhk.avro.datamodel.StructType.Field.NULL_VALUE;
import static opwvhk.avro.datamodel.TestStructures.enumType;
import static opwvhk.avro.datamodel.TestStructures.optional;
import static opwvhk.avro.datamodel.TestStructures.required;
import static opwvhk.avro.datamodel.TestStructures.struct;
import static opwvhk.avro.datamodel.TestStructures.unparsed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TypeStructureTest {
	@Test
	public void testAvroConversions() {
		Schema schema = new Schema.Parser().parse(AVRO_SCHEMA);
		Type structure = Type.fromSchema(schema);
		assertThat(structure.toString()).isEqualTo(TYPE_STRUCTURE.replace("@NULL@", JsonProperties.NULL_VALUE.toString()));

		// The type structure does not support some inefficiencies allowed by Avro
		Schema schema2 = new Schema.Parser().parse(AVRO_SCHEMA.replace(
						"\"type\": [\"null\", { \"type\": \"array\", \"items\": [\"null\", \"string\"] }], \"default\": null,",
						"\"type\": { \"type\": \"array\", \"items\": \"string\" }, \"default\": [],")
				.replace("{\"type\": \"string\", \"logicalType\": \"uuid\"}", "\"string\"").replace("[\"string\"]", "\"string\""));
		assertThat(structure.toSchema()).isEqualTo(schema2);
	}

	@Test
	public void testAvroDefaultValues() {
		StructType struct = struct("defaults").withFields(optional("optional1", null, FixedType.STRING, StructType.Field.NULL_VALUE),
				optional("optional2", null, FixedType.STRING, JsonProperties.NULL_VALUE),
				optional("optional3", null, FixedType.STRING, Schema.Field.NULL_DEFAULT_VALUE), optional("optional4", null, FixedType.STRING, "text"));
		Schema schema = new Schema.Parser().parse("""
				{"type": "record", "name": "defaults", "fields": [
					{"name": "optional1", "type": ["null", "string"], "default": null},
					{"name": "optional2", "type": ["null", "string"], "default": null},
					{"name": "optional3", "type": ["null", "string"], "default": null},
					{"name": "optional4", "type": ["string", "null"], "default": "text"}
				]}""");
		assertThat(struct.toSchema()).isEqualTo(schema);
	}

	@Test
	public void testAvroConversionFailures() {
		Schema fixedSchema = Schema.createFixed("not.supported", null, null, 8);
		assertThatThrownBy(() -> Type.fromSchema(fixedSchema)).isInstanceOf(IllegalArgumentException.class);

		Schema complexUnion1 = new Schema.Parser().parse(
				"{\"type\":\"record\",\"name\":\"record\",\"fields\":[{\"name\":\"complexUnion\",\"type\":[\"int\",\"string\"]}]}");
		assertThatThrownBy(() -> Type.fromSchema(complexUnion1)).isInstanceOf(IllegalArgumentException.class);
		Schema complexUnion2 = new Schema.Parser().parse(
				"{\"type\":\"record\",\"name\":\"record\",\"fields\":[{\"name\":\"complexUnion\",\"type\":[\"null\",\"int\",\"string\"]}]}");
		assertThatThrownBy(() -> Type.fromSchema(complexUnion2)).isInstanceOf(IllegalArgumentException.class);
		Schema uselessUnion = new Schema.Parser().parse(
				"{\"type\":\"record\",\"name\":\"record\",\"fields\":[{\"name\":\"uselessUnion\",\"type\":[\"null\"]}]}");
		assertThatThrownBy(() -> Type.fromSchema(uselessUnion)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void testNamingRules() {
		assertThatThrownBy(() -> new StructType(new TypeCollection(), "names", Set.of("may", "not", "repeat", "names"), null)).isInstanceOf(
				IllegalArgumentException.class);

		assertThatThrownBy(
				() -> new StructType.Field("names", Set.of("may", "not", "repeat", "names"), null, Cardinality.REQUIRED, FixedType.STRING, null)).isInstanceOf(
				IllegalArgumentException.class);

		assertThatThrownBy(() -> new EnumType(new TypeCollection(), "names", Set.of("may", "not", "repeat", "names"), null, List.of(), null)).isInstanceOf(
				IllegalArgumentException.class);

		TypeCollection typeCollection = new TypeCollection();
		new StructType(typeCollection, "someType", null);
		StructType type = new StructType(typeCollection, "unique", "Names and aliases must be unique");
		assertThatThrownBy(() -> type.rename("someType")).isInstanceOf(IllegalArgumentException.class);

		StructType.Field field1 = required("alias", null, FixedType.STRING, null);
		field1.rename("field1");
		field1.rename("alias"); // Renaming back is allowed
		assertThat(field1.name()).isEqualTo("alias");
		assertThat(field1.aliases()).containsExactly("field1");
		StructType.Field field2 = required("field2", Set.of("alias"), null, FixedType.STRING, null);
		StructType.Field field1a = required("alias", Set.of(), null, FixedType.STRING, null);
		assertThatThrownBy(() -> type.setFields(List.of(field1, field2, field1a))).isInstanceOf(IllegalArgumentException.class);

		StructType.Field field3 = required("field3", null, FixedType.STRING, null);
		type.setFields(List.of(field1, field3));
		assertThatThrownBy(() -> field3.rename("alias")).isInstanceOf(IllegalArgumentException.class);

		EnumType enumType = new EnumType(typeCollection, "enum", Set.of(), null, List.of("NO", "MAYBE", "YES"), "MAYBE");
		assertThatThrownBy(() -> enumType.rename("unique")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void testSimpleScalarParsing() {
		assertThat(FixedType.BOOLEAN.parse(null)).isNull();
		assertThat(FixedType.FLOAT.parse(null)).isNull();
		assertThat(FixedType.DOUBLE.parse(null)).isNull();
		assertThat(FixedType.DATE.parse(null)).isNull();
		assertThat(FixedType.DATETIME.parse(null)).isNull();
		assertThat(FixedType.DATETIME_MICROS.parse(null)).isNull();
		assertThat(FixedType.TIME.parse(null)).isNull();
		assertThat(FixedType.TIME_MICROS.parse(null)).isNull();
		assertThat(FixedType.STRING.parse(null)).isNull();
		assertThat(FixedType.BINARY_HEX.parse(null)).isNull();
		assertThat(FixedType.BINARY_BASE64.parse(null)).isNull();

		assertThat(FixedType.BOOLEAN.parse("true")).isEqualTo(true);
		assertThat(FixedType.FLOAT.parse("12.34")).isInstanceOf(Float.class).isEqualTo(12.34f);
		assertThat(FixedType.DOUBLE.parse("12.34")).isInstanceOf(Double.class).isEqualTo(12.34);
		assertThat(FixedType.DATE.parse("2023-03-16")).isEqualTo(LocalDate.of(2023, MARCH, 16));
		Instant timestamp = ZonedDateTime.of(2023, 3, 16, 4, 49, 0, 781_354_000, UTC).toInstant();
		assertThat(FixedType.DATETIME.parse("2023-03-16T04:49:00.781z")).isEqualTo(timestamp.truncatedTo(ChronoUnit.MILLIS));
		assertThat(FixedType.DATETIME_MICROS.parse("2023-03-16T04:49:00.781354z")).isEqualTo(timestamp.truncatedTo(ChronoUnit.MICROS));
		LocalTime time = LocalTime.of(12,43,56,78_912_000);
		assertThat(FixedType.TIME.parse("12:43:56.078")).isEqualTo(time.truncatedTo(ChronoUnit.MILLIS));
		assertThat(FixedType.TIME_MICROS.parse("12:43:56.078912")).isEqualTo(time.truncatedTo(ChronoUnit.MICROS));
		assertThat(FixedType.STRING.parse("some text")).isEqualTo("some text");
		assertThat(FixedType.BINARY_HEX.parse("DEAD")).isEqualTo(ByteBuffer.wrap(bytes(0, 222, 173)));
		assertThat(FixedType.BINARY_BASE64.parse("U2ltcGxlIHRleHQ=")).isEqualTo(ByteBuffer.wrap("Simple text".getBytes(UTF_8)));
	}

	@Test
	public void testDecimals() {
		DecimalType smallInteger = DecimalType.integer(20, 7);
		DecimalType largeInteger = DecimalType.integer(40, 13);
		DecimalType hugeInteger = DecimalType.integer(70, 22);
		DecimalType fractional = DecimalType.withFraction(6, 2);
		assertThatThrownBy(() -> new DecimalType(1, 2, 3)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> DecimalType.integer(-12, 12)).isInstanceOf(IllegalArgumentException.class);

		assertThat(smallInteger.parse(null)).isNull();
		assertThat(largeInteger.parse(null)).isNull();
		assertThat(hugeInteger.parse(null)).isNull();
		assertThat(fractional.parse(null)).isNull();

		assertThat(smallInteger.parse("12")).isEqualTo(12);
		assertThat(largeInteger.parse("12")).isEqualTo(12L);
		assertThat(hugeInteger.parse("12")).isEqualTo(BigDecimal.valueOf(12));
		assertThat(fractional.parse("12")).isEqualTo(BigDecimal.valueOf(1200, 2));

		assertThatThrownBy(() -> smallInteger.parse("12.34")).isInstanceOf(NumberFormatException.class);
		assertThatThrownBy(() -> largeInteger.parse("12.34")).isInstanceOf(NumberFormatException.class);
		assertThatThrownBy(() -> hugeInteger.parse("12.34")).isInstanceOf(ArithmeticException.class);
		assertThat(fractional.parse("12.34")).isEqualTo(BigDecimal.valueOf(1234, 2));

		assertThatThrownBy(() -> fractional.parse("12.345")).isInstanceOf(ArithmeticException.class);
		assertThatThrownBy(() -> fractional.parse("123456.78")).isInstanceOf(ArithmeticException.class);
	}

	@Test
	public void testEnums() {
		assertThatThrownBy(() -> new EnumType(new TypeCollection(), "name", null, List.of("the", "default", "symbol", "must"), "exist")).isInstanceOf(
				IllegalArgumentException.class);

		EnumType withoutDefault = new EnumType(new TypeCollection(), "name", Set.of("alias"), "Description", List.of("one", "two"), null);
		assertThat(withoutDefault.defaultSymbol()).isNull();
		EnumType withDefault = new EnumType(new TypeCollection(), "name", Set.of("alias"), "Description", List.of("one", "two"), "one");
		assertThat(withDefault.defaultSymbol()).isEqualTo("one");

		assertThat(withoutDefault.debugString("")).isEqualTo("enum(name, alias: one, two)");
		assertThat(withDefault.debugString("")).isEqualTo("enum(name, alias: one, two; one)");

		assertThat(withoutDefault.parse(null)).isNull();
		assertThat(withoutDefault.parse("one")).isEqualTo("one");
		assertThat(withoutDefault.parse("two")).isEqualTo("two");
		assertThatThrownBy(() -> withoutDefault.parse("three")).isInstanceOf(IllegalArgumentException.class);

		assertThat(withDefault.parse(null)).isNull();
		assertThat(withDefault.parse("one")).isEqualTo("one");
		assertThat(withDefault.parse("two")).isEqualTo("two");
		assertThat(withDefault.parse("three")).isEqualTo("one");
	}

	@Test
	public void testUnparsedContent() {
		Type type = unparsed(FixedType.STRING);
		assertThat(type.toString()).isEqualTo("(unparsed) string");
		assertThat(type.debugString("> ")).isEqualTo("> (unparsed) string");
	}

	@Test
	public void testUpdatedNamesAndDocumentation() {
		StructType nestedBefore = struct("nested").withFields(optional("field1", null, DecimalType.INTEGER_TYPE, NULL_VALUE),
				required("field2a", Set.of("field2b"), "Many names", FixedType.STRING, null),
				required("status", enumType("Status", "switch", List.of("ON", "OFF"))));
		StructType before = struct("before", "gibberish").withFields(required("name", FixedType.STRING), optional("description", FixedType.STRING),
				required("nested", nestedBefore));

		EnumType enumType = new EnumType(null, "Switch", Set.of("Status"), "On/Off switch", List.of("ON", "OFF"), null);
		StructType nestedAfter = struct("ranked", Set.of("nested"), "Now with ranked fields").withFields(
				optional("first", Set.of("field1"), "Winner!", DecimalType.INTEGER_TYPE, NULL_VALUE),
				required("second", Set.of("field2a", "field2b"), "Many names", FixedType.STRING, null),
				required("toggle", Set.of("status"), "Flip me!", enumType, null));
		StructType after = struct("after", Set.of("before"), "Some sensible comment").withFields(required("title", Set.of("name"), FixedType.STRING),
				optional("notes", Set.of("description"), "Notes are helpful", FixedType.STRING, null),
				required("ranking", Set.of("nested"), "Ranking, but no stars", nestedAfter, null));

		updateFieldDoc(before, "Winner!", "nested", "field1");
		updateTypeDoc(before, "On/Off switch", "nested", "status");
		updateFieldDoc(before, "Flip me!", "nested", "status");
		updateFieldDoc(before, "Notes are helpful", "description");
		updateFieldDoc(before, "Ranking, but no stars", "nested");
		updateTypeDoc(before, "Now with ranked fields", "nested");
		updateTypeDoc(before, "Some sensible comment");

		renameField(before, "first", "nested", "field1");
		renameField(before, "second", "nested", "field2b");
		renameType(before, "Switch", "nested", "status");
		renameField(before, "toggle", "nested", "status");
		renameType(before, "ranked", "nested");
		renameField(before, "ranking", "nested");
		renameField(before, "title", "name");
		renameField(before, "notes", "description");
		renameType(before, "after");

		assertThat(before).isEqualTo(after);
	}

	private static void updateFieldDoc(StructType type, String newDocumentation, String firstField, String... otherFields) {
		StructType.Field structField = type.getField(firstField);
		for (String otherField : otherFields) {
			structField = ((StructType) structField.type()).getField(otherField);
		}
		structField.setDocumentation(newDocumentation);
	}

	private static void updateTypeDoc(Type type, String newDocumentation, String... fields) {
		for (String field : fields) {
			type = ((StructType) type).getField(field).type();
		}
		if (type instanceof StructType structType) {
			structType.setDocumentation(newDocumentation);
		} else if (type instanceof EnumType enumType) {
			enumType.setDocumentation(newDocumentation);
		} else {
			throw new IllegalArgumentException("Cannot set documentation on undocumented type: " + type);
		}
	}

	private static void renameField(StructType type, String newName, String firstField, String... otherFields) {
		StructType.Field structField = type.getField(firstField);
		for (String otherField : otherFields) {
			structField = ((StructType) structField.type()).getField(otherField);
		}
		structField.rename(newName);
	}

	private static void renameType(Type type, String newName, String... fields) {
		for (String field : fields) {
			type = ((StructType) type).getField(field).type();
		}
		if (type instanceof StructType structType) {
			structType.rename(newName);
		} else if (type instanceof EnumType enumType) {
			enumType.rename(newName);
		} else {
			throw new IllegalArgumentException("Cannot rename unnamed type: " + type);
		}
	}

	@Test
	public void testStructuralEdgeCases() {
		TypeCollection typeCollection = new TypeCollection();
		StructType type = struct(typeCollection, "name", Set.of("alias"), null);
		assertThat(type.toString()).isEqualTo("""
				StructType(name, alias) {
				  (no fields yet)
				}""");

		StructType.Field field1 = required("field1", Set.of("alias1", "alias2"), null, FixedType.STRING, null);
		type.setFields(List.of(field1, required("field2", null, FixedType.STRING, null)));
		assertThat(type.toString()).isEqualTo("""
				StructType(name, alias) {
				  field1, alias1, alias2
				    string
				  field2
				    string
				}""");
		assertThat(type.getField("field1")).isEqualTo(field1);
		assertThat(type.getField("alias1")).isEqualTo(field1);
		assertThat(type.getField("alias2")).isEqualTo(field1);
		assertThat(type.getField("alias2").hashCode()).isEqualTo(field1.hashCode());

		assertThatThrownBy(() -> new StructType(typeCollection, "alias", null)).isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> type.setFields(type.fields().subList(0, 1))).isInstanceOf(IllegalStateException.class);

		StructType otherType = new StructType(typeCollection, "otherType", null);
		assertThatThrownBy(() -> otherType.setFields(type.fields())).isInstanceOf(IllegalStateException.class);

		assertThat(typeCollection.getType(type.name())).isEqualTo(type);

		//noinspection DataFlowIssue
		assertThatThrownBy(() -> new StructType.Field("typeIsRequired", null, Cardinality.REQUIRED, null, null)).isInstanceOf(NullPointerException.class);

		StructType type1 = struct("name1");
		type1.setFields(List.of());
		StructType type2 = struct("name2");
		type2.setFields(List.of());
		StructType type3 = struct("name1", Set.of("alias"), null);
		type3.setFields(List.of());
		StructType type4 = struct("name1", "doc");
		type4.setFields(List.of());
		StructType type5 = struct("name1").withFields(required("field", null, FixedType.STRING, null));
		StructType type6 = struct("name1").withFields(required("field", null, FixedType.STRING, null));

		assertThat(type1).isEqualTo(type1);
		assertThat(type5).isEqualTo(type6);
		assertThat(type1).isNotEqualTo(null);
		//noinspection AssertBetweenInconvertibleTypes
		assertThat(type1).isNotEqualTo("mismatch");
		assertThat(type1).isNotEqualTo(type2);
		assertThat(type1).isNotEqualTo(type3);
		assertThat(type1).isNotEqualTo(type4);
		assertThat(type1).isNotEqualTo(type5);
		assertThat(type5).isNotEqualTo(type1);
	}

	private static byte[] bytes(int... bytes) {
		byte[] result = new byte[bytes.length];
		for (int i = 0; i < bytes.length; i++) {
			result[i] = (byte) bytes[i];
		}
		return result;
	}

	private static final String AVRO_SCHEMA = """
			{
				"type": "record",
				"name": "ns.Fields",
				"aliases": ["ns.TestCases"],
				"doc": "A near-flat record with all Avro-supported scalar types",
				"fields": [
					{"name": "bool", "type": "boolean", "doc": "boolean"},
					{"name": "bin", "type": "bytes", "doc": "binary"},
					{"name": "d_number", "type": "double", "doc": "approximate number"},
					{"name": "f_number", "type": "float", "doc": "inaccurate number"},
					{"name": "i_number", "type": "int", "doc": "number"},
					{"name": "l_number", "type": "long", "doc": "large number"},
					{"name": "s_number", "type": {"type": "bytes", "logicalType": "decimal", "precision": 9, "scale": 2},
						"doc": "a number with specified precision"},
					{"name": "h_number", "type": {"type": "bytes", "logicalType": "decimal", "precision": 20, "scale": 0},
						"doc": "a huge integer number"},
					{"name": "text", "type": "string", "doc": "a bit of text"},
					{"name": "silly", "type": ["string"], "doc": "a union of one non-null type is silly, but supported"},
					{"name": "date", "type": {"type": "int", "logicalType": "date"}, "doc": "a date"},
					{"name": "datetime", "type": {"type": "long", "logicalType": "timestamp-millis"}, "doc": "a timestamp"},
					{"name": "datetime_u", "type": {"type": "long", "logicalType": "timestamp-micros"}, "doc": "a precise timestamp"},
					{"name": "time", "type": {"type": "int", "logicalType": "time-millis"}, "doc": "a time of day"},
					{"name": "time_u", "type": {"type": "long", "logicalType": "time-micros"}, "doc": "a precise time of day"},
					{"name": "status", "type": {"type": "enum", "name": "toggle", "symbols": ["on", "off"], "default": "off"}, "doc": "a switch"},
					{"name": "structure", "doc": "structural elements", "type":  {
						"type": "record",
						"name": "cardinality",
						"fields": [
							{"name": "numbers", "type": {"type": "array", "items": "int"}, "default": []},
							{"name": "description", "type": ["string", "null"], "default": "..."},
							{"name": "remark", "type": ["null", "string"], "default": null},
							{"name": "optimized", "type": ["null", { "type": "array", "items": ["null", "string"] }], "default": null,
								"doc": "Nesting arrays and unions is silly, and will be optimized away."
							}
						]}
					},
					{"name": "structure2", "doc": "structural elements, repeated", "type": "cardinality"},
					{"name": "raw", "type": {"type": "string", "logicalType": "uuid"}, "doc": "unsupported logical types are dropped"}
			]}
			""";
	private static final String TYPE_STRUCTURE = """
			StructType(ns.Fields, ns.TestCases) {
			  Doc: A near-flat record with all Avro-supported scalar types
			  bin
			    Doc: binary
			    binary_hex
			  bool
			    Doc: boolean
			    boolean
			  d_number
			    Doc: approximate number
			    double
			  date
			    Doc: a date
			    date
			  datetime
			    Doc: a timestamp
			    datetime
			  datetime_u
			    Doc: a precise timestamp
			    datetime_micros
			  f_number
			    Doc: inaccurate number
			    float
			  h_number
			    Doc: a huge integer number
			    decimal(20; 67 bits)
			  i_number
			    Doc: number
			    decimal(10; 32 bits)
			  l_number
			    Doc: large number
			    decimal(19; 64 bits)
			  raw
			    Doc: unsupported logical types are dropped
			    string
			  s_number
			    Doc: a number with specified precision
			    decimal(9,2)
			  silly
			    Doc: a union of one non-null type is silly, but supported
			    string
			  status
			    Doc: a switch
			    enum(ns.toggle: on, off; off)
			  structure
			    Doc: structural elements
			    StructType(ns.cardinality) {
			      description?
			        Default: ...
			        string
			      numbers[]
			        Default: []
			        decimal(10; 32 bits)
			      optimized[]
			        Doc: Nesting arrays and unions is silly, and will be optimized away.
			        Default: []
			        string
			      remark?
			        Default: @NULL@
			        string
			    }
			  structure2
			    Doc: structural elements, repeated
			    StructType(ns.cardinality)
			  text
			    Doc: a bit of text
			    string
			  time
			    Doc: a time of day
			    time
			  time_u
			    Doc: a precise time of day
			    time_micros
			}""";
}