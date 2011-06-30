// $ANTLR 2.7.6 (2005-12-22): "StreamItParserFE.g" -> "StreamItParserFE.java"$

	package streamit.frontend;

	import streamit.frontend.nodes.*;

	import java.util.Collections;
	import java.io.DataInputStream;
	import java.util.List;

	import java.util.ArrayList;

public interface StreamItParserFETokenTypes {
	int EOF = 1;
	int NULL_TREE_LOOKAHEAD = 3;
	int TK_filter = 4;
	int TK_pipeline = 5;
	int TK_splitjoin = 6;
	int TK_feedbackloop = 7;
	int TK_portal = 8;
	int TK_to = 9;
	int TK_handler = 10;
	int TK_add = 11;
	int TK_split = 12;
	int TK_join = 13;
	int TK_duplicate = 14;
	int TK_roundrobin = 15;
	int TK_body = 16;
	int TK_loop = 17;
	int TK_enqueue = 18;
	int TK_init = 19;
	int TK_prework = 20;
	int TK_work = 21;
	int TK_peek = 22;
	int TK_pop = 23;
	int TK_push = 24;
	int TK_boolean = 25;
	int TK_float = 26;
	int TK_bit = 27;
	int TK_int = 28;
	int TK_void = 29;
	int TK_double = 30;
	int TK_complex = 31;
	int TK_float2 = 32;
	int TK_float3 = 33;
	int TK_float4 = 34;
	int TK_struct = 35;
	int TK_template = 36;
	int TK_native = 37;
	int TK_static = 38;
	int TK_helper = 39;
	int TK_if = 40;
	int TK_else = 41;
	int TK_while = 42;
	int TK_for = 43;
	int TK_switch = 44;
	int TK_case = 45;
	int TK_default = 46;
	int TK_break = 47;
	int TK_continue = 48;
	int TK_return = 49;
	int TK_do = 50;
	int TK_pi = 51;
	int TK_true = 52;
	int TK_false = 53;
	int ARROW = 54;
	int WS = 55;
	int SL_COMMENT = 56;
	int ML_COMMENT = 57;
	int LPAREN = 58;
	int RPAREN = 59;
	int LCURLY = 60;
	int RCURLY = 61;
	int LSQUARE = 62;
	int RSQUARE = 63;
	int PLUS = 64;
	int PLUS_EQUALS = 65;
	int INCREMENT = 66;
	int MINUS = 67;
	int MINUS_EQUALS = 68;
	int DECREMENT = 69;
	int STAR = 70;
	int STAR_EQUALS = 71;
	int DIV = 72;
	int DIV_EQUALS = 73;
	int MOD = 74;
	int LOGIC_AND = 75;
	int LOGIC_OR = 76;
	int BITWISE_AND = 77;
	int BITWISE_OR = 78;
	int BITWISE_XOR = 79;
	int BITWISE_COMPLEMENT = 80;
	int LSHIFT = 81;
	int RSHIFT = 82;
	int LSHIFT_EQUALS = 83;
	int RSHIFT_EQUALS = 84;
	int ASSIGN = 85;
	int EQUAL = 86;
	int NOT_EQUAL = 87;
	int LESS_THAN = 88;
	int LESS_EQUAL = 89;
	int MORE_THAN = 90;
	int MORE_EQUAL = 91;
	int QUESTION = 92;
	int COLON = 93;
	int SEMI = 94;
	int COMMA = 95;
	int DOT = 96;
	int BANG = 97;
	int CHAR_LITERAL = 98;
	int STRING_LITERAL = 99;
	int ESC = 100;
	int DIGIT = 101;
	int HEXNUMBER = 102;
	int NUMBER = 103;
	int ID = 104;
}
