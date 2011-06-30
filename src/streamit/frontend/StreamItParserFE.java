// $ANTLR 2.7.6 (2005-12-22): "StreamItParserFE.g" -> "StreamItParserFE.java"$

	package streamit.frontend;

	import streamit.frontend.nodes.*;

	import java.util.Collections;
	import java.io.DataInputStream;
	import java.util.List;

	import java.util.ArrayList;

import antlr.TokenBuffer;
import antlr.TokenStreamException;
import antlr.TokenStreamIOException;
import antlr.ANTLRException;
import antlr.LLkParser;
import antlr.Token;
import antlr.TokenStream;
import antlr.RecognitionException;
import antlr.NoViableAltException;
import antlr.MismatchedTokenException;
import antlr.SemanticException;
import antlr.ParserSharedInputState;
import antlr.collections.impl.BitSet;

public class StreamItParserFE extends antlr.LLkParser       implements StreamItParserFETokenTypes
 {

	public static void main(String[] args)
	{
		try
		{
			DataInputStream dis = new DataInputStream(System.in);
			StreamItLex lexer = new StreamItLex(dis);
			StreamItParserFE parser = new StreamItParserFE(lexer);
			parser.program();
		}
		catch (Exception e)
		{
			e.printStackTrace(System.err);
		}
	}

	public FEContext getContext(Token t)
	{
		int line = t.getLine();
		if (line == 0) line = -1;
		int col = t.getColumn();
		if (col == 0) col = -1;
		return new FEContext(getFilename(), line, col);
	}

	private boolean hasError = false;

	public void reportError(RecognitionException ex)
	{
		hasError = true;
		super.reportError(ex);
	}

	public void  reportError(String s)
	{
		hasError = true;
		super.reportError(s);
	}

protected StreamItParserFE(TokenBuffer tokenBuf, int k) {
  super(tokenBuf,k);
  tokenNames = _tokenNames;
}

public StreamItParserFE(TokenBuffer tokenBuf) {
  this(tokenBuf,1);
}

protected StreamItParserFE(TokenStream lexer, int k) {
  super(lexer,k);
  tokenNames = _tokenNames;
}

public StreamItParserFE(TokenStream lexer) {
  this(lexer,1);
}

public StreamItParserFE(ParserSharedInputState state) {
  super(state,1);
  tokenNames = _tokenNames;
}

	public final Program  program() throws RecognitionException, TokenStreamException {
		Program p;
		
		p = null; 
			List structs = new ArrayList(); 
			List streams = new ArrayList();
			List helpers = new ArrayList();
			TypeStruct ts; StreamSpec ss; TypeHelper th;
		
		try {      // for error handling
			{
			_loop3:
			do {
				switch ( LA(1)) {
				case TK_struct:
				{
					ts=struct_decl();
					if ( inputState.guessing==0 ) {
						structs.add(ts);
					}
					break;
				}
				case TK_portal:
				case TK_boolean:
				case TK_float:
				case TK_bit:
				case TK_int:
				case TK_void:
				case TK_double:
				case TK_complex:
				case TK_float2:
				case TK_float3:
				case TK_float4:
				case ID:
				{
					ss=stream_decl();
					if ( inputState.guessing==0 ) {
						streams.add(ss);
					}
					break;
				}
				case TK_native:
				{
					th=native_decl();
					if ( inputState.guessing==0 ) {
						helpers.add(th);
					}
					break;
				}
				case TK_helper:
				{
					th=helper_decl();
					if ( inputState.guessing==0 ) {
						helpers.add(th);
					}
					break;
				}
				case TK_static:
				{
					ss=global_decl();
					if ( inputState.guessing==0 ) {
						streams.add(ss);
					}
					break;
				}
				default:
				{
					break _loop3;
				}
				}
			} while (true);
			}
			match(Token.EOF_TYPE);
			if ( inputState.guessing==0 ) {
				if (!hasError) p = new Program(null, streams, structs, helpers);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_0);
			} else {
			  throw ex;
			}
		}
		return p;
	}
	
	public final TypeStruct  struct_decl() throws RecognitionException, TokenStreamException {
		TypeStruct ts;
		
		Token  t = null;
		Token  id = null;
		ts = null; Parameter p; List names = new ArrayList();
			List types = new ArrayList();
		
		try {      // for error handling
			t = LT(1);
			match(TK_struct);
			id = LT(1);
			match(ID);
			match(LCURLY);
			{
			_loop208:
			do {
				if ((_tokenSet_1.member(LA(1)))) {
					p=param_decl();
					match(SEMI);
					if ( inputState.guessing==0 ) {
						names.add(p.getName()); types.add(p.getType());
					}
				}
				else {
					break _loop208;
				}
				
			} while (true);
			}
			match(RCURLY);
			if ( inputState.guessing==0 ) {
				ts = new TypeStruct(getContext(t), id.getText(), names, types);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_2);
			} else {
			  throw ex;
			}
		}
		return ts;
	}
	
	public final StreamSpec  stream_decl() throws RecognitionException, TokenStreamException {
		StreamSpec ss;
		
		ss = null; StreamType st;
		
		try {      // for error handling
			st=stream_type_decl();
			{
			switch ( LA(1)) {
			case TK_filter:
			{
				ss=filter_decl(st);
				break;
			}
			case TK_pipeline:
			case TK_splitjoin:
			case TK_feedbackloop:
			{
				ss=struct_stream_decl(st);
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_2);
			} else {
			  throw ex;
			}
		}
		return ss;
	}
	
	public final TypeHelper  native_decl() throws RecognitionException, TokenStreamException {
		TypeHelper th;
		
		Token  t = null;
		Token  id = null;
		th = null; List funcs = new ArrayList(); Function fn; int cls = TypeHelper.NATIVE_HELPERS;
		
		try {      // for error handling
			t = LT(1);
			match(TK_native);
			id = LT(1);
			match(ID);
			match(LCURLY);
			{
			_loop215:
			do {
				boolean synPredMatched213 = false;
				if (((_tokenSet_1.member(LA(1))))) {
					int _m213 = mark();
					synPredMatched213 = true;
					inputState.guessing++;
					try {
						{
						data_type();
						match(ID);
						match(LPAREN);
						}
					}
					catch (RecognitionException pe) {
						synPredMatched213 = false;
					}
					rewind(_m213);
inputState.guessing--;
				}
				if ( synPredMatched213 ) {
					{
					fn=native_function_decl();
					}
					if ( inputState.guessing==0 ) {
						funcs.add(fn);
					}
				}
				else {
					break _loop215;
				}
				
			} while (true);
			}
			match(RCURLY);
			if ( inputState.guessing==0 ) {
				th = new TypeHelper(getContext(t), id.getText(), funcs, cls);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_2);
			} else {
			  throw ex;
			}
		}
		return th;
	}
	
	public final TypeHelper  helper_decl() throws RecognitionException, TokenStreamException {
		TypeHelper th;
		
		Token  t = null;
		Token  id = null;
		th = null; List funcs = new ArrayList(); Function fn;
		
		try {      // for error handling
			t = LT(1);
			match(TK_helper);
			id = LT(1);
			match(ID);
			match(LCURLY);
			{
			_loop221:
			do {
				boolean synPredMatched219 = false;
				if (((_tokenSet_1.member(LA(1))))) {
					int _m219 = mark();
					synPredMatched219 = true;
					inputState.guessing++;
					try {
						{
						data_type();
						match(ID);
						match(LPAREN);
						}
					}
					catch (RecognitionException pe) {
						synPredMatched219 = false;
					}
					rewind(_m219);
inputState.guessing--;
				}
				if ( synPredMatched219 ) {
					{
					fn=function_decl();
					}
					if ( inputState.guessing==0 ) {
						funcs.add(fn);
					}
				}
				else {
					break _loop221;
				}
				
			} while (true);
			}
			match(RCURLY);
			if ( inputState.guessing==0 ) {
				th = new TypeHelper(getContext(t), id.getText(), funcs);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_2);
			} else {
			  throw ex;
			}
		}
		return th;
	}
	
	public final StreamSpec  global_decl() throws RecognitionException, TokenStreamException {
		StreamSpec ss;
		
		Token  tg = null;
		ss = null; FEContext context = null; StreamSpec body;
		
		try {      // for error handling
			tg = LT(1);
			match(TK_static);
			if ( inputState.guessing==0 ) {
				context = getContext(tg);
			}
			body=global_body(context);
			if ( inputState.guessing==0 ) {
				ss = body;
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_2);
			} else {
			  throw ex;
			}
		}
		return ss;
	}
	
	public final StreamType  stream_type_decl() throws RecognitionException, TokenStreamException {
		StreamType st;
		
		Token  t = null;
		st = null; Type in, out;
		
		try {      // for error handling
			in=data_type();
			t = LT(1);
			match(ARROW);
			out=data_type();
			if ( inputState.guessing==0 ) {
				st = new StreamType(getContext(t), in, out);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_3);
			} else {
			  throw ex;
			}
		}
		return st;
	}
	
	public final StreamSpec  filter_decl(
		StreamType st
	) throws RecognitionException, TokenStreamException {
		StreamSpec ss;
		
		Token  tf = null;
		Token  id = null;
		ss = null; List params = Collections.EMPTY_LIST; FEContext context = null;
		
		try {      // for error handling
			tf = LT(1);
			match(TK_filter);
			if ( inputState.guessing==0 ) {
				if (st != null) context = st.getContext();
							else context = getContext(tf);
			}
			id = LT(1);
			match(ID);
			{
			switch ( LA(1)) {
			case LPAREN:
			{
				params=param_decl_list();
				break;
			}
			case LCURLY:
			{
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			}
			ss=filter_body(context, st, id.getText(), params);
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_2);
			} else {
			  throw ex;
			}
		}
		return ss;
	}
	
	public final StreamSpec  struct_stream_decl(
		StreamType st
	) throws RecognitionException, TokenStreamException {
		StreamSpec ss;
		
		Token  id = null;
		ss = null; int type = 0;
			List params = Collections.EMPTY_LIST; Statement body;
		
		try {      // for error handling
			{
			switch ( LA(1)) {
			case TK_pipeline:
			{
				match(TK_pipeline);
				if ( inputState.guessing==0 ) {
					type = StreamSpec.STREAM_PIPELINE;
				}
				break;
			}
			case TK_splitjoin:
			{
				match(TK_splitjoin);
				if ( inputState.guessing==0 ) {
					type = StreamSpec.STREAM_SPLITJOIN;
				}
				break;
			}
			case TK_feedbackloop:
			{
				match(TK_feedbackloop);
				if ( inputState.guessing==0 ) {
					type = StreamSpec.STREAM_FEEDBACKLOOP;
				}
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			}
			id = LT(1);
			match(ID);
			{
			switch ( LA(1)) {
			case LPAREN:
			{
				params=param_decl_list();
				break;
			}
			case LCURLY:
			{
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			}
			body=block();
			if ( inputState.guessing==0 ) {
				ss = new StreamSpec(st.getContext(), type, st, id.getText(),
								params, body);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_2);
			} else {
			  throw ex;
			}
		}
		return ss;
	}
	
	public final StreamSpec  global_body(
		FEContext context
	) throws RecognitionException, TokenStreamException {
		StreamSpec ss;
		
		ss = null; List vars = new ArrayList(); List funcs = new ArrayList();
			Function fn; FieldDecl decl;
		
		try {      // for error handling
			match(LCURLY);
			{
			_loop9:
			do {
				if ((_tokenSet_1.member(LA(1)))) {
					decl=field_decl();
					match(SEMI);
					if ( inputState.guessing==0 ) {
						vars.add(decl);
					}
				}
				else {
					break _loop9;
				}
				
			} while (true);
			}
			{
			switch ( LA(1)) {
			case TK_init:
			{
				fn=init_decl();
				if ( inputState.guessing==0 ) {
					funcs.add(fn);
				}
				break;
			}
			case RCURLY:
			{
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			}
			match(RCURLY);
			if ( inputState.guessing==0 ) {
				StreamType st = new StreamType(context, 
						                           new TypePrimitive(TypePrimitive.TYPE_VOID),
						                           new TypePrimitive(TypePrimitive.TYPE_VOID));
				ss = new StreamSpec(context, StreamSpec.STREAM_GLOBAL, st,
				"TheGlobal", Collections.EMPTY_LIST,
				vars, funcs);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_2);
			} else {
			  throw ex;
			}
		}
		return ss;
	}
	
	public final FieldDecl  field_decl() throws RecognitionException, TokenStreamException {
		FieldDecl f;
		
		Token  id = null;
		Token  id2 = null;
		f = null; Type t; Expression x = null;
			List<Type> ts = new ArrayList<Type>(); List<String> ns = new ArrayList<String>();
			List<Expression> xs = new ArrayList<Expression>(); FEContext ctx = null;
		
		try {      // for error handling
			t=data_type();
			id = LT(1);
			match(ID);
			{
			switch ( LA(1)) {
			case ASSIGN:
			{
				match(ASSIGN);
				x=var_initializer();
				break;
			}
			case SEMI:
			case COMMA:
			{
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			}
			if ( inputState.guessing==0 ) {
				ctx = getContext(id); ts.add(t); ns.add(id.getText()); xs.add(x);
			}
			{
			_loop27:
			do {
				if ((LA(1)==COMMA)) {
					if ( inputState.guessing==0 ) {
						x = null;
					}
					match(COMMA);
					id2 = LT(1);
					match(ID);
					{
					switch ( LA(1)) {
					case ASSIGN:
					{
						match(ASSIGN);
						x=var_initializer();
						break;
					}
					case SEMI:
					case COMMA:
					{
						break;
					}
					default:
					{
						throw new NoViableAltException(LT(1), getFilename());
					}
					}
					}
					if ( inputState.guessing==0 ) {
						ts.add(t); ns.add(id2.getText()); xs.add(x);
					}
				}
				else {
					break _loop27;
				}
				
			} while (true);
			}
			if ( inputState.guessing==0 ) {
				f = new FieldDecl(ctx, ts, ns, xs);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_4);
			} else {
			  throw ex;
			}
		}
		return f;
	}
	
	public final Function  init_decl() throws RecognitionException, TokenStreamException {
		Function f;
		
		Token  t = null;
		Statement s; f = null;
		
		try {      // for error handling
			t = LT(1);
			match(TK_init);
			s=block();
			if ( inputState.guessing==0 ) {
				f = Function.newInit(getContext(t), s);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_5);
			} else {
			  throw ex;
			}
		}
		return f;
	}
	
	public final Statement  global_statement() throws RecognitionException, TokenStreamException {
		Statement s;
		
		s = null;
		
		try {      // for error handling
			switch ( LA(1)) {
			case TK_if:
			{
				s=if_else_statement();
				break;
			}
			case TK_while:
			{
				s=while_statement();
				break;
			}
			case TK_do:
			{
				s=do_while_statement();
				match(SEMI);
				break;
			}
			case TK_for:
			{
				s=for_statement();
				break;
			}
			case SEMI:
			{
				match(SEMI);
				break;
			}
			default:
				boolean synPredMatched13 = false;
				if (((_tokenSet_6.member(LA(1))))) {
					int _m13 = mark();
					synPredMatched13 = true;
					inputState.guessing++;
					try {
						{
						expr_statement();
						}
					}
					catch (RecognitionException pe) {
						synPredMatched13 = false;
					}
					rewind(_m13);
inputState.guessing--;
				}
				if ( synPredMatched13 ) {
					s=expr_statement();
					match(SEMI);
				}
				else {
					boolean synPredMatched15 = false;
					if (((LA(1)==ID))) {
						int _m15 = mark();
						synPredMatched15 = true;
						inputState.guessing++;
						try {
							{
							match(ID);
							match(DOT);
							match(ID);
							match(LPAREN);
							}
						}
						catch (RecognitionException pe) {
							synPredMatched15 = false;
						}
						rewind(_m15);
inputState.guessing--;
					}
					if ( synPredMatched15 ) {
						s=helper_call_statement();
						match(SEMI);
					}
				else {
					throw new NoViableAltException(LT(1), getFilename());
				}
				}}
			}
			catch (RecognitionException ex) {
				if (inputState.guessing==0) {
					reportError(ex);
					recover(ex,_tokenSet_0);
				} else {
				  throw ex;
				}
			}
			return s;
		}
		
	public final Statement  expr_statement() throws RecognitionException, TokenStreamException {
		Statement s;
		
		s = null; Expression x;
		
		try {      // for error handling
			boolean synPredMatched131 = false;
			if (((LA(1)==INCREMENT||LA(1)==DECREMENT||LA(1)==ID))) {
				int _m131 = mark();
				synPredMatched131 = true;
				inputState.guessing++;
				try {
					{
					incOrDec();
					}
				}
				catch (RecognitionException pe) {
					synPredMatched131 = false;
				}
				rewind(_m131);
inputState.guessing--;
			}
			if ( synPredMatched131 ) {
				x=incOrDec();
				if ( inputState.guessing==0 ) {
					s = new StmtExpr(x);
				}
			}
			else {
				boolean synPredMatched134 = false;
				if (((LA(1)==ID))) {
					int _m134 = mark();
					synPredMatched134 = true;
					inputState.guessing++;
					try {
						{
						left_expr();
						{
						switch ( LA(1)) {
						case ASSIGN:
						{
							match(ASSIGN);
							break;
						}
						case PLUS_EQUALS:
						{
							match(PLUS_EQUALS);
							break;
						}
						case MINUS_EQUALS:
						{
							match(MINUS_EQUALS);
							break;
						}
						case STAR_EQUALS:
						{
							match(STAR_EQUALS);
							break;
						}
						case DIV_EQUALS:
						{
							match(DIV_EQUALS);
							break;
						}
						case LSHIFT_EQUALS:
						{
							match(LSHIFT_EQUALS);
							break;
						}
						case RSHIFT_EQUALS:
						{
							match(RSHIFT_EQUALS);
							break;
						}
						default:
						{
							throw new NoViableAltException(LT(1), getFilename());
						}
						}
						}
						}
					}
					catch (RecognitionException pe) {
						synPredMatched134 = false;
					}
					rewind(_m134);
inputState.guessing--;
				}
				if ( synPredMatched134 ) {
					s=assign_expr();
				}
				else {
					boolean synPredMatched136 = false;
					if (((LA(1)==ID))) {
						int _m136 = mark();
						synPredMatched136 = true;
						inputState.guessing++;
						try {
							{
							match(ID);
							match(LPAREN);
							}
						}
						catch (RecognitionException pe) {
							synPredMatched136 = false;
						}
						rewind(_m136);
inputState.guessing--;
					}
					if ( synPredMatched136 ) {
						x=func_call();
						if ( inputState.guessing==0 ) {
							s = new StmtExpr(x);
						}
					}
					else if ((LA(1)==TK_peek||LA(1)==TK_pop)) {
						x=streamit_value_expr();
						if ( inputState.guessing==0 ) {
							s = new StmtExpr(x);
						}
					}
					else {
						throw new NoViableAltException(LT(1), getFilename());
					}
					}}
				}
				catch (RecognitionException ex) {
					if (inputState.guessing==0) {
						reportError(ex);
						recover(ex,_tokenSet_7);
					} else {
					  throw ex;
					}
				}
				return s;
			}
			
	public final Statement  if_else_statement() throws RecognitionException, TokenStreamException {
		Statement s;
		
		Token  u = null;
		s = null; Expression x; Statement t, f = null;
		
		try {      // for error handling
			u = LT(1);
			match(TK_if);
			match(LPAREN);
			x=right_expr();
			match(RPAREN);
			t=statement();
			{
			boolean synPredMatched115 = false;
			if (((LA(1)==TK_else))) {
				int _m115 = mark();
				synPredMatched115 = true;
				inputState.guessing++;
				try {
					{
					match(TK_else);
					}
				}
				catch (RecognitionException pe) {
					synPredMatched115 = false;
				}
				rewind(_m115);
inputState.guessing--;
			}
			if ( synPredMatched115 ) {
				{
				match(TK_else);
				f=statement();
				}
			}
			else if ((_tokenSet_8.member(LA(1)))) {
			}
			else {
				throw new NoViableAltException(LT(1), getFilename());
			}
			
			}
			if ( inputState.guessing==0 ) {
				s = new StmtIfThen(getContext(u), x, t, f);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_8);
			} else {
			  throw ex;
			}
		}
		return s;
	}
	
	public final Statement  while_statement() throws RecognitionException, TokenStreamException {
		Statement s;
		
		Token  t = null;
		s = null; Expression x; Statement b;
		
		try {      // for error handling
			t = LT(1);
			match(TK_while);
			match(LPAREN);
			x=right_expr();
			match(RPAREN);
			b=statement();
			if ( inputState.guessing==0 ) {
				s = new StmtWhile(getContext(t), x, b);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_8);
			} else {
			  throw ex;
			}
		}
		return s;
	}
	
	public final Statement  do_while_statement() throws RecognitionException, TokenStreamException {
		Statement s;
		
		Token  t = null;
		s = null; Expression x; Statement b;
		
		try {      // for error handling
			t = LT(1);
			match(TK_do);
			b=statement();
			match(TK_while);
			match(LPAREN);
			x=right_expr();
			match(RPAREN);
			if ( inputState.guessing==0 ) {
				s = new StmtDoWhile(getContext(t), b, x);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_4);
			} else {
			  throw ex;
			}
		}
		return s;
	}
	
	public final Statement  for_statement() throws RecognitionException, TokenStreamException {
		Statement s;
		
		Token  t = null;
		s = null; Expression x=null; Statement a, b, c;
		
		try {      // for error handling
			t = LT(1);
			match(TK_for);
			match(LPAREN);
			a=for_init_statement();
			match(SEMI);
			{
			switch ( LA(1)) {
			case TK_peek:
			case TK_pop:
			case TK_float2:
			case TK_float3:
			case TK_float4:
			case TK_pi:
			case TK_true:
			case TK_false:
			case LPAREN:
			case INCREMENT:
			case MINUS:
			case DECREMENT:
			case BITWISE_COMPLEMENT:
			case BANG:
			case CHAR_LITERAL:
			case STRING_LITERAL:
			case HEXNUMBER:
			case NUMBER:
			case ID:
			{
				x=right_expr();
				break;
			}
			case SEMI:
			{
				if ( inputState.guessing==0 ) {
					x = new ExprConstBoolean(getContext(t), true);
				}
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			}
			match(SEMI);
			b=for_incr_statement();
			match(RPAREN);
			c=statement();
			if ( inputState.guessing==0 ) {
				s = new StmtFor(getContext(t), a, x, b, c);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_8);
			} else {
			  throw ex;
			}
		}
		return s;
	}
	
	public final Statement  helper_call_statement() throws RecognitionException, TokenStreamException {
		Statement s;
		
		Token  p = null;
		Token  m = null;
		s = null; List l;
		
		try {      // for error handling
			p = LT(1);
			match(ID);
			match(DOT);
			m = LT(1);
			match(ID);
			l=func_call_params();
			if ( inputState.guessing==0 ) {
				s = new StmtHelperCall(getContext(p), p.getText(), m.getText(), l);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_4);
			} else {
			  throw ex;
			}
		}
		return s;
	}
	
	public final List  param_decl_list() throws RecognitionException, TokenStreamException {
		List l;
		
		l = new ArrayList(); Parameter p;
		
		try {      // for error handling
			match(LPAREN);
			{
			switch ( LA(1)) {
			case TK_portal:
			case TK_boolean:
			case TK_float:
			case TK_bit:
			case TK_int:
			case TK_void:
			case TK_double:
			case TK_complex:
			case TK_float2:
			case TK_float3:
			case TK_float4:
			case ID:
			{
				p=param_decl();
				if ( inputState.guessing==0 ) {
					l.add(p);
				}
				{
				_loop105:
				do {
					if ((LA(1)==COMMA)) {
						match(COMMA);
						p=param_decl();
						if ( inputState.guessing==0 ) {
							l.add(p);
						}
					}
					else {
						break _loop105;
					}
					
				} while (true);
				}
				break;
			}
			case RPAREN:
			{
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			}
			match(RPAREN);
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_9);
			} else {
			  throw ex;
			}
		}
		return l;
	}
	
	public final StreamSpec  filter_body(
		FEContext context, StreamType st, String name, List params
	) throws RecognitionException, TokenStreamException {
		StreamSpec ss;
		
		ss = null; List vars = new ArrayList(); List funcs = new ArrayList();
			Function fn; FieldDecl decl;
		
		try {      // for error handling
			match(LCURLY);
			{
			_loop22:
			do {
				switch ( LA(1)) {
				case TK_init:
				{
					fn=init_decl();
					if ( inputState.guessing==0 ) {
						funcs.add(fn);
					}
					break;
				}
				case TK_prework:
				case TK_work:
				{
					fn=work_decl();
					if ( inputState.guessing==0 ) {
						funcs.add(fn);
					}
					break;
				}
				case TK_handler:
				{
					fn=handler_decl();
					if ( inputState.guessing==0 ) {
						funcs.add(fn);
					}
					break;
				}
				default:
					boolean synPredMatched21 = false;
					if (((_tokenSet_1.member(LA(1))))) {
						int _m21 = mark();
						synPredMatched21 = true;
						inputState.guessing++;
						try {
							{
							data_type();
							match(ID);
							match(LPAREN);
							}
						}
						catch (RecognitionException pe) {
							synPredMatched21 = false;
						}
						rewind(_m21);
inputState.guessing--;
					}
					if ( synPredMatched21 ) {
						fn=function_decl();
						if ( inputState.guessing==0 ) {
							funcs.add(fn);
						}
					}
					else if ((_tokenSet_1.member(LA(1)))) {
						decl=field_decl();
						match(SEMI);
						if ( inputState.guessing==0 ) {
							vars.add(decl);
						}
					}
				else {
					break _loop22;
				}
				}
			} while (true);
			}
			match(RCURLY);
			if ( inputState.guessing==0 ) {
				ss = new StreamSpec(context, StreamSpec.STREAM_FILTER,
								st, name, params, vars, funcs);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_10);
			} else {
			  throw ex;
			}
		}
		return ss;
	}
	
	public final FuncWork  work_decl() throws RecognitionException, TokenStreamException {
		FuncWork f;
		
		Token  tw = null;
		Token  tpw = null;
			f = null;
			Expression pop = null, peek = null, push = null;
			Statement s; FEContext c = null; String name = null;
			int type = 0;
		
		
		try {      // for error handling
			{
			switch ( LA(1)) {
			case TK_work:
			{
				tw = LT(1);
				match(TK_work);
				if ( inputState.guessing==0 ) {
					c = getContext(tw); type = Function.FUNC_WORK;
				}
				break;
			}
			case TK_prework:
			{
				tpw = LT(1);
				match(TK_prework);
				if ( inputState.guessing==0 ) {
					c = getContext(tpw);
												 type = Function.FUNC_PREWORK;
				}
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			}
			{
			_loop35:
			do {
				switch ( LA(1)) {
				case TK_push:
				{
					match(TK_push);
					push=rate_expr();
					break;
				}
				case TK_pop:
				{
					match(TK_pop);
					pop=rate_expr();
					break;
				}
				case TK_peek:
				{
					match(TK_peek);
					peek=rate_expr();
					break;
				}
				default:
				{
					break _loop35;
				}
				}
			} while (true);
			}
			s=block();
			if ( inputState.guessing==0 ) {
				f = new FuncWork(c, type, name, s, peek, pop, push);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_5);
			} else {
			  throw ex;
			}
		}
		return f;
	}
	
	public final Type  data_type() throws RecognitionException, TokenStreamException {
		Type t;
		
		Token  id = null;
		Token  l = null;
		Token  pn = null;
		t = null; Type primitive = null; Expression x; 
		ArrayList lengths = new ArrayList();
		
		try {      // for error handling
			switch ( LA(1)) {
			case TK_boolean:
			case TK_float:
			case TK_bit:
			case TK_int:
			case TK_double:
			case TK_complex:
			case TK_float2:
			case TK_float3:
			case TK_float4:
			case ID:
			{
				{
				switch ( LA(1)) {
				case TK_boolean:
				case TK_float:
				case TK_bit:
				case TK_int:
				case TK_double:
				case TK_complex:
				case TK_float2:
				case TK_float3:
				case TK_float4:
				{
					t=primitive_type();
					break;
				}
				case ID:
				{
					id = LT(1);
					match(ID);
					if ( inputState.guessing==0 ) {
						t = new TypeStructRef(id.getText());
					}
					break;
				}
				default:
				{
					throw new NoViableAltException(LT(1), getFilename());
				}
				}
				}
				{
				_loop91:
				do {
					if ((LA(1)==LSQUARE)) {
						l = LT(1);
						match(LSQUARE);
						{
						switch ( LA(1)) {
						case TK_peek:
						case TK_pop:
						case TK_float2:
						case TK_float3:
						case TK_float4:
						case TK_pi:
						case TK_true:
						case TK_false:
						case LPAREN:
						case INCREMENT:
						case MINUS:
						case DECREMENT:
						case BITWISE_COMPLEMENT:
						case BANG:
						case CHAR_LITERAL:
						case STRING_LITERAL:
						case HEXNUMBER:
						case NUMBER:
						case ID:
						{
							x=right_expr();
							if ( inputState.guessing==0 ) {
								lengths.add(x);
							}
							break;
						}
						case RSQUARE:
						{
							if ( inputState.guessing==0 ) {
								throw new SemanticException("missing array bounds in type declaration", getFilename(), l.getLine());
							}
							break;
						}
						default:
						{
							throw new NoViableAltException(LT(1), getFilename());
						}
						}
						}
						match(RSQUARE);
					}
					else {
						break _loop91;
					}
					
				} while (true);
				}
				if ( inputState.guessing==0 ) {
					
					for (int i=lengths.size()-1; i>=0; i--) {
					t = new TypeArray(t, (Expression)lengths.get(i));
					}
					
				}
				break;
			}
			case TK_void:
			{
				match(TK_void);
				if ( inputState.guessing==0 ) {
					t = new TypePrimitive(TypePrimitive.TYPE_VOID);
				}
				break;
			}
			case TK_portal:
			{
				match(TK_portal);
				match(LESS_THAN);
				pn = LT(1);
				match(ID);
				match(MORE_THAN);
				if ( inputState.guessing==0 ) {
					t = new TypePortal(pn.getText());
				}
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_11);
			} else {
			  throw ex;
			}
		}
		return t;
	}
	
	public final Function  function_decl() throws RecognitionException, TokenStreamException {
		Function f;
		
		Token  id = null;
		
		Type t; List l; Statement s; f = null;
		int cls = Function.FUNC_HELPER;
			Expression pop = null, peek = null, push = null;
		
		
		try {      // for error handling
			t=data_type();
			id = LT(1);
			match(ID);
			l=param_decl_list();
			{
			_loop100:
			do {
				switch ( LA(1)) {
				case TK_push:
				{
					match(TK_push);
					push=rate_expr();
					break;
				}
				case TK_pop:
				{
					match(TK_pop);
					pop=rate_expr();
					break;
				}
				case TK_peek:
				{
					match(TK_peek);
					peek=rate_expr();
					break;
				}
				default:
				{
					break _loop100;
				}
				}
			} while (true);
			}
			s=block();
			if ( inputState.guessing==0 ) {
				f = new Function(getContext(id), cls, id.getText(), t, l, s, peek, pop, push);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_5);
			} else {
			  throw ex;
			}
		}
		return f;
	}
	
	public final Function  handler_decl() throws RecognitionException, TokenStreamException {
		Function f;
		
		Token  id = null;
		List l; Statement s; f = null;
		Type t = new TypePrimitive(TypePrimitive.TYPE_VOID);
		int cls = Function.FUNC_HANDLER;
		
		try {      // for error handling
			match(TK_handler);
			id = LT(1);
			match(ID);
			l=param_decl_list();
			s=block();
			if ( inputState.guessing==0 ) {
				f = new Function(getContext(id), cls, id.getText(), t, l, s, null, null, null);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_5);
			} else {
			  throw ex;
			}
		}
		return f;
	}
	
	public final Expression  var_initializer() throws RecognitionException, TokenStreamException {
		Expression x;
		
		x = null;
		
		try {      // for error handling
			switch ( LA(1)) {
			case TK_peek:
			case TK_pop:
			case TK_float2:
			case TK_float3:
			case TK_float4:
			case TK_pi:
			case TK_true:
			case TK_false:
			case LPAREN:
			case INCREMENT:
			case MINUS:
			case DECREMENT:
			case BITWISE_COMPLEMENT:
			case BANG:
			case CHAR_LITERAL:
			case STRING_LITERAL:
			case HEXNUMBER:
			case NUMBER:
			case ID:
			{
				x=right_expr();
				break;
			}
			case LCURLY:
			{
				x=arr_initializer();
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_12);
			} else {
			  throw ex;
			}
		}
		return x;
	}
	
	public final Statement  block() throws RecognitionException, TokenStreamException {
		Statement s;
		
		Token  t = null;
		s = null; List l = new ArrayList();
		
		try {      // for error handling
			t = LT(1);
			match(LCURLY);
			{
			_loop109:
			do {
				if ((_tokenSet_13.member(LA(1)))) {
					s=statement();
					if ( inputState.guessing==0 ) {
						if (s != null) l.add(s);
					}
				}
				else {
					break _loop109;
				}
				
			} while (true);
			}
			match(RCURLY);
			if ( inputState.guessing==0 ) {
				s = new StmtBlock(getContext(t), l);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_14);
			} else {
			  throw ex;
			}
		}
		return s;
	}
	
	public final Expression  rate_expr() throws RecognitionException, TokenStreamException {
		Expression e;
		
		Token  s = null;
		e = null;
		
		try {      // for error handling
			switch ( LA(1)) {
			case STAR:
			{
				{
				s = LT(1);
				match(STAR);
				if ( inputState.guessing==0 ) {
					// convert plain '*' to range '[*,*,*]' for consistency with SIR + library
					e = new ExprRange(getContext(s),
					new ExprDynamicToken(getContext(s)),
					new ExprDynamicToken(getContext(s)),
					new ExprDynamicToken(getContext(s)));
				}
				}
				break;
			}
			case LSQUARE:
			{
				e=range_expr();
				break;
			}
			case TK_peek:
			case TK_pop:
			case TK_float2:
			case TK_float3:
			case TK_float4:
			case TK_pi:
			case TK_true:
			case TK_false:
			case LPAREN:
			case INCREMENT:
			case MINUS:
			case DECREMENT:
			case BITWISE_COMPLEMENT:
			case BANG:
			case CHAR_LITERAL:
			case STRING_LITERAL:
			case HEXNUMBER:
			case NUMBER:
			case ID:
			{
				e=right_expr();
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_15);
			} else {
			  throw ex;
			}
		}
		return e;
	}
	
	public final Expression  range_expr() throws RecognitionException, TokenStreamException {
		Expression e;
		
		Token  l = null;
		
		e = null;
		FEContext c = null;
		Expression min = null, r2 = null, r3 = null; 
		
		
		try {      // for error handling
			{
			l = LT(1);
			match(LSQUARE);
			if ( inputState.guessing==0 ) {
				c = getContext(l);
			}
			min=dynamic_expr();
			match(COMMA);
			r2=dynamic_expr();
			{
			switch ( LA(1)) {
			case COMMA:
			{
				match(COMMA);
				r3=dynamic_expr();
				break;
			}
			case RSQUARE:
			{
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			}
			match(RSQUARE);
			}
			if ( inputState.guessing==0 ) {
				Expression ave, max;
				if (r3==null) {
				ave = null;
				max = r2;
				} else {
				ave = r2;
				max = r3;
				}
				e = new ExprRange(c, min, ave, max); 
				
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_15);
			} else {
			  throw ex;
			}
		}
		return e;
	}
	
	public final Expression  right_expr() throws RecognitionException, TokenStreamException {
		Expression x;
		
		x = null;
		
		try {      // for error handling
			switch ( LA(1)) {
			case TK_peek:
			case TK_pop:
			case TK_pi:
			case TK_true:
			case TK_false:
			case LPAREN:
			case INCREMENT:
			case MINUS:
			case DECREMENT:
			case BITWISE_COMPLEMENT:
			case BANG:
			case CHAR_LITERAL:
			case STRING_LITERAL:
			case HEXNUMBER:
			case NUMBER:
			case ID:
			{
				x=ternaryExpr();
				break;
			}
			case TK_float2:
			{
				x=float2_initializer();
				break;
			}
			case TK_float3:
			{
				x=float3_initializer();
				break;
			}
			case TK_float4:
			{
				x=float4_initializer();
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_16);
			} else {
			  throw ex;
			}
		}
		return x;
	}
	
	public final Expression  dynamic_expr() throws RecognitionException, TokenStreamException {
		Expression e;
		
		Token  s = null;
		
		e = null;
		
		
		try {      // for error handling
			switch ( LA(1)) {
			case STAR:
			{
				{
				s = LT(1);
				match(STAR);
				if ( inputState.guessing==0 ) {
					e = new ExprDynamicToken(getContext(s));
				}
				}
				break;
			}
			case TK_peek:
			case TK_pop:
			case TK_float2:
			case TK_float3:
			case TK_float4:
			case TK_pi:
			case TK_true:
			case TK_false:
			case LPAREN:
			case INCREMENT:
			case MINUS:
			case DECREMENT:
			case BITWISE_COMPLEMENT:
			case BANG:
			case CHAR_LITERAL:
			case STRING_LITERAL:
			case HEXNUMBER:
			case NUMBER:
			case ID:
			{
				e=right_expr();
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_17);
			} else {
			  throw ex;
			}
		}
		return e;
	}
	
	public final Statement  push_statement() throws RecognitionException, TokenStreamException {
		Statement s;
		
		Token  t = null;
		s = null; Expression x;
		
		try {      // for error handling
			t = LT(1);
			match(TK_push);
			match(LPAREN);
			x=right_expr();
			match(RPAREN);
			if ( inputState.guessing==0 ) {
				s = new StmtPush(getContext(t), x);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_4);
			} else {
			  throw ex;
			}
		}
		return s;
	}
	
	public final List  func_call_params() throws RecognitionException, TokenStreamException {
		List l;
		
		l = new ArrayList(); Expression x;
		
		try {      // for error handling
			match(LPAREN);
			{
			switch ( LA(1)) {
			case TK_peek:
			case TK_pop:
			case TK_float2:
			case TK_float3:
			case TK_float4:
			case TK_pi:
			case TK_true:
			case TK_false:
			case LPAREN:
			case INCREMENT:
			case MINUS:
			case DECREMENT:
			case BITWISE_COMPLEMENT:
			case BANG:
			case CHAR_LITERAL:
			case STRING_LITERAL:
			case HEXNUMBER:
			case NUMBER:
			case ID:
			{
				x=right_expr();
				if ( inputState.guessing==0 ) {
					l.add(x);
				}
				{
				_loop144:
				do {
					if ((LA(1)==COMMA)) {
						match(COMMA);
						x=right_expr();
						if ( inputState.guessing==0 ) {
							l.add(x);
						}
					}
					else {
						break _loop144;
					}
					
				} while (true);
				}
				break;
			}
			case RPAREN:
			{
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			}
			match(RPAREN);
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_18);
			} else {
			  throw ex;
			}
		}
		return l;
	}
	
	public final Statement  msg_statement() throws RecognitionException, TokenStreamException {
		Statement s;
		
		Token  p = null;
		Token  m = null;
		s = null; List l;
		Expression minl = null, maxl = null; boolean var = false;
		
		try {      // for error handling
			p = LT(1);
			match(ID);
			match(DOT);
			m = LT(1);
			match(ID);
			l=func_call_params();
			{
			switch ( LA(1)) {
			case LSQUARE:
			{
				match(LSQUARE);
				{
				switch ( LA(1)) {
				case TK_peek:
				case TK_pop:
				case TK_float2:
				case TK_float3:
				case TK_float4:
				case TK_pi:
				case TK_true:
				case TK_false:
				case LPAREN:
				case INCREMENT:
				case MINUS:
				case DECREMENT:
				case BITWISE_COMPLEMENT:
				case COLON:
				case BANG:
				case CHAR_LITERAL:
				case STRING_LITERAL:
				case HEXNUMBER:
				case NUMBER:
				case ID:
				{
					{
					switch ( LA(1)) {
					case TK_peek:
					case TK_pop:
					case TK_float2:
					case TK_float3:
					case TK_float4:
					case TK_pi:
					case TK_true:
					case TK_false:
					case LPAREN:
					case INCREMENT:
					case MINUS:
					case DECREMENT:
					case BITWISE_COMPLEMENT:
					case BANG:
					case CHAR_LITERAL:
					case STRING_LITERAL:
					case HEXNUMBER:
					case NUMBER:
					case ID:
					{
						minl=right_expr();
						break;
					}
					case COLON:
					{
						break;
					}
					default:
					{
						throw new NoViableAltException(LT(1), getFilename());
					}
					}
					}
					match(COLON);
					{
					switch ( LA(1)) {
					case TK_peek:
					case TK_pop:
					case TK_float2:
					case TK_float3:
					case TK_float4:
					case TK_pi:
					case TK_true:
					case TK_false:
					case LPAREN:
					case INCREMENT:
					case MINUS:
					case DECREMENT:
					case BITWISE_COMPLEMENT:
					case BANG:
					case CHAR_LITERAL:
					case STRING_LITERAL:
					case HEXNUMBER:
					case NUMBER:
					case ID:
					{
						maxl=right_expr();
						break;
					}
					case RSQUARE:
					{
						break;
					}
					default:
					{
						throw new NoViableAltException(LT(1), getFilename());
					}
					}
					}
					break;
				}
				case STAR:
				{
					match(STAR);
					if ( inputState.guessing==0 ) {
						var = true;
					}
					break;
				}
				default:
				{
					throw new NoViableAltException(LT(1), getFilename());
				}
				}
				}
				match(RSQUARE);
				break;
			}
			case SEMI:
			{
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			}
			if ( inputState.guessing==0 ) {
				if (! var) {
						 		if (minl == null) { minl = new ExprConstInt(0);}
								if (maxl == null) { maxl = minl; }
						    }
							s = new StmtSendMessage(getContext(p),
								new ExprVar(getContext(p), p.getText()),
								m.getText(), l, minl, maxl);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_4);
			} else {
			  throw ex;
			}
		}
		return s;
	}
	
	public final Statement  statement() throws RecognitionException, TokenStreamException {
		Statement s;
		
		Token  tb = null;
		Token  tc = null;
		s = null;
		
		try {      // for error handling
			switch ( LA(1)) {
			case TK_add:
			{
				s=add_statement();
				break;
			}
			case TK_body:
			{
				s=body_statement();
				break;
			}
			case TK_loop:
			{
				s=loop_statement();
				break;
			}
			case TK_split:
			{
				s=split_statement();
				match(SEMI);
				break;
			}
			case TK_join:
			{
				s=join_statement();
				match(SEMI);
				break;
			}
			case TK_enqueue:
			{
				s=enqueue_statement();
				match(SEMI);
				break;
			}
			case TK_push:
			{
				s=push_statement();
				match(SEMI);
				break;
			}
			case LCURLY:
			{
				s=block();
				break;
			}
			case TK_break:
			{
				tb = LT(1);
				match(TK_break);
				match(SEMI);
				if ( inputState.guessing==0 ) {
					s = new StmtBreak(getContext(tb));
				}
				break;
			}
			case TK_continue:
			{
				tc = LT(1);
				match(TK_continue);
				match(SEMI);
				if ( inputState.guessing==0 ) {
					s = new StmtContinue(getContext(tc));
				}
				break;
			}
			case TK_return:
			{
				s=return_statement();
				match(SEMI);
				break;
			}
			case TK_if:
			{
				s=if_else_statement();
				break;
			}
			case TK_while:
			{
				s=while_statement();
				break;
			}
			case TK_do:
			{
				s=do_while_statement();
				match(SEMI);
				break;
			}
			case TK_for:
			{
				s=for_statement();
				break;
			}
			case SEMI:
			{
				match(SEMI);
				break;
			}
			default:
				boolean synPredMatched53 = false;
				if (((_tokenSet_1.member(LA(1))))) {
					int _m53 = mark();
					synPredMatched53 = true;
					inputState.guessing++;
					try {
						{
						data_type();
						match(ID);
						}
					}
					catch (RecognitionException pe) {
						synPredMatched53 = false;
					}
					rewind(_m53);
inputState.guessing--;
				}
				if ( synPredMatched53 ) {
					s=variable_decl();
					match(SEMI);
				}
				else {
					boolean synPredMatched55 = false;
					if (((_tokenSet_6.member(LA(1))))) {
						int _m55 = mark();
						synPredMatched55 = true;
						inputState.guessing++;
						try {
							{
							expr_statement();
							}
						}
						catch (RecognitionException pe) {
							synPredMatched55 = false;
						}
						rewind(_m55);
inputState.guessing--;
					}
					if ( synPredMatched55 ) {
						s=expr_statement();
						match(SEMI);
					}
					else if ((LA(1)==ID)) {
						s=msg_statement();
						match(SEMI);
					}
				else {
					throw new NoViableAltException(LT(1), getFilename());
				}
				}}
			}
			catch (RecognitionException ex) {
				if (inputState.guessing==0) {
					reportError(ex);
					recover(ex,_tokenSet_8);
				} else {
				  throw ex;
				}
			}
			return s;
		}
		
	public final Statement  add_statement() throws RecognitionException, TokenStreamException {
		Statement s;
		
		Token  t = null;
		s = null; StreamCreator sc;
		
		try {      // for error handling
			t = LT(1);
			match(TK_add);
			sc=stream_creator();
			if ( inputState.guessing==0 ) {
				s = new StmtAdd(getContext(t), sc);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_8);
			} else {
			  throw ex;
			}
		}
		return s;
	}
	
	public final Statement  body_statement() throws RecognitionException, TokenStreamException {
		Statement s;
		
		Token  t = null;
		s = null; StreamCreator sc;
		
		try {      // for error handling
			t = LT(1);
			match(TK_body);
			sc=stream_creator();
			if ( inputState.guessing==0 ) {
				s = new StmtBody(getContext(t), sc);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_8);
			} else {
			  throw ex;
			}
		}
		return s;
	}
	
	public final Statement  loop_statement() throws RecognitionException, TokenStreamException {
		Statement s;
		
		Token  t = null;
		s = null; StreamCreator sc;
		
		try {      // for error handling
			t = LT(1);
			match(TK_loop);
			sc=stream_creator();
			if ( inputState.guessing==0 ) {
				s = new StmtLoop(getContext(t), sc);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_8);
			} else {
			  throw ex;
			}
		}
		return s;
	}
	
	public final Statement  split_statement() throws RecognitionException, TokenStreamException {
		Statement s;
		
		Token  t = null;
		s = null; SplitterJoiner sj;
		
		try {      // for error handling
			t = LT(1);
			match(TK_split);
			sj=splitter_or_joiner();
			if ( inputState.guessing==0 ) {
				s = new StmtSplit(getContext(t), sj);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_4);
			} else {
			  throw ex;
			}
		}
		return s;
	}
	
	public final Statement  join_statement() throws RecognitionException, TokenStreamException {
		Statement s;
		
		Token  t = null;
		s = null; SplitterJoiner sj;
		
		try {      // for error handling
			t = LT(1);
			match(TK_join);
			sj=splitter_or_joiner();
			if ( inputState.guessing==0 ) {
				s = new StmtJoin(getContext(t), sj);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_4);
			} else {
			  throw ex;
			}
		}
		return s;
	}
	
	public final Statement  enqueue_statement() throws RecognitionException, TokenStreamException {
		Statement s;
		
		Token  t = null;
		s = null; Expression x;
		
		try {      // for error handling
			t = LT(1);
			match(TK_enqueue);
			x=right_expr();
			if ( inputState.guessing==0 ) {
				s = new StmtEnqueue(getContext(t), x);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_4);
			} else {
			  throw ex;
			}
		}
		return s;
	}
	
	public final Statement  variable_decl() throws RecognitionException, TokenStreamException {
		Statement s;
		
		Token  id = null;
		Token  id2 = null;
		s = null; Type t; Expression x = null; 
			List<Type> ts = new ArrayList<Type>(); List<String> ns = new ArrayList<String>();
			List<Expression> xs = new ArrayList(); FEContext ctx = null;
		
		try {      // for error handling
			t=data_type();
			id = LT(1);
			match(ID);
			if ( inputState.guessing==0 ) {
				ctx = getContext(id);
			}
			{
			switch ( LA(1)) {
			case ASSIGN:
			{
				match(ASSIGN);
				x=var_initializer();
				break;
			}
			case SEMI:
			case COMMA:
			{
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			}
			if ( inputState.guessing==0 ) {
				ts.add(t); ns.add(id.getText()); xs.add(x);
			}
			{
			_loop97:
			do {
				if ((LA(1)==COMMA)) {
					if ( inputState.guessing==0 ) {
						x = null;
					}
					match(COMMA);
					id2 = LT(1);
					match(ID);
					{
					switch ( LA(1)) {
					case ASSIGN:
					{
						match(ASSIGN);
						x=var_initializer();
						break;
					}
					case SEMI:
					case COMMA:
					{
						break;
					}
					default:
					{
						throw new NoViableAltException(LT(1), getFilename());
					}
					}
					}
					if ( inputState.guessing==0 ) {
						ts.add(t); ns.add(id2.getText()); xs.add(x);
					}
				}
				else {
					break _loop97;
				}
				
			} while (true);
			}
			if ( inputState.guessing==0 ) {
				s = new StmtVarDecl(ctx, ts, ns, xs);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_4);
			} else {
			  throw ex;
			}
		}
		return s;
	}
	
	public final Statement  return_statement() throws RecognitionException, TokenStreamException {
		Statement s;
		
		Token  t = null;
		s = null; Expression x = null;
		
		try {      // for error handling
			t = LT(1);
			match(TK_return);
			{
			switch ( LA(1)) {
			case TK_peek:
			case TK_pop:
			case TK_float2:
			case TK_float3:
			case TK_float4:
			case TK_pi:
			case TK_true:
			case TK_false:
			case LPAREN:
			case INCREMENT:
			case MINUS:
			case DECREMENT:
			case BITWISE_COMPLEMENT:
			case BANG:
			case CHAR_LITERAL:
			case STRING_LITERAL:
			case HEXNUMBER:
			case NUMBER:
			case ID:
			{
				x=right_expr();
				break;
			}
			case SEMI:
			{
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			}
			if ( inputState.guessing==0 ) {
				s = new StmtReturn(getContext(t), x);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_4);
			} else {
			  throw ex;
			}
		}
		return s;
	}
	
	public final StreamCreator  stream_creator() throws RecognitionException, TokenStreamException {
		StreamCreator sc;
		
		sc = null;
		
		try {      // for error handling
			boolean synPredMatched61 = false;
			if (((_tokenSet_19.member(LA(1))))) {
				int _m61 = mark();
				synPredMatched61 = true;
				inputState.guessing++;
				try {
					{
					switch ( LA(1)) {
					case ID:
					{
						match(ID);
						match(ARROW);
						break;
					}
					case TK_filter:
					case TK_pipeline:
					case TK_splitjoin:
					case TK_feedbackloop:
					case TK_portal:
					case TK_to:
					case TK_handler:
					case TK_add:
					case TK_split:
					case TK_join:
					case TK_duplicate:
					case TK_roundrobin:
					case TK_body:
					case TK_loop:
					case TK_enqueue:
					case TK_init:
					case TK_prework:
					case TK_work:
					case TK_peek:
					case TK_pop:
					case TK_push:
					case TK_boolean:
					case TK_float:
					case TK_bit:
					case TK_int:
					case TK_void:
					case TK_double:
					case TK_complex:
					case TK_float2:
					case TK_float3:
					case TK_float4:
					case TK_struct:
					case TK_template:
					case TK_native:
					case TK_static:
					case TK_helper:
					case TK_if:
					case TK_else:
					case TK_while:
					case TK_for:
					case TK_switch:
					case TK_case:
					case TK_default:
					case TK_break:
					case TK_continue:
					case TK_return:
					case TK_do:
					case TK_pi:
					case TK_true:
					case TK_false:
					case ARROW:
					case WS:
					case SL_COMMENT:
					case ML_COMMENT:
					case LPAREN:
					case RPAREN:
					case LCURLY:
					case RCURLY:
					case LSQUARE:
					case RSQUARE:
					case PLUS:
					case PLUS_EQUALS:
					case INCREMENT:
					case MINUS:
					case MINUS_EQUALS:
					case DECREMENT:
					case STAR:
					case STAR_EQUALS:
					case DIV:
					case DIV_EQUALS:
					case MOD:
					case LOGIC_AND:
					case LOGIC_OR:
					case BITWISE_AND:
					case BITWISE_OR:
					case BITWISE_XOR:
					case BITWISE_COMPLEMENT:
					case LSHIFT:
					case RSHIFT:
					case LSHIFT_EQUALS:
					case RSHIFT_EQUALS:
					case ASSIGN:
					case EQUAL:
					case NOT_EQUAL:
					case LESS_THAN:
					case LESS_EQUAL:
					case MORE_THAN:
					case MORE_EQUAL:
					case QUESTION:
					case COLON:
					case SEMI:
					case COMMA:
					case DOT:
					case BANG:
					case CHAR_LITERAL:
					case STRING_LITERAL:
					case ESC:
					case DIGIT:
					case HEXNUMBER:
					case NUMBER:
					{
						matchNot(ID);
						break;
					}
					default:
					{
						throw new NoViableAltException(LT(1), getFilename());
					}
					}
					}
				}
				catch (RecognitionException pe) {
					synPredMatched61 = false;
				}
				rewind(_m61);
inputState.guessing--;
			}
			if ( synPredMatched61 ) {
				sc=anonymous_stream();
			}
			else if ((LA(1)==ID)) {
				sc=named_stream();
				match(SEMI);
			}
			else {
				throw new NoViableAltException(LT(1), getFilename());
			}
			
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_8);
			} else {
			  throw ex;
			}
		}
		return sc;
	}
	
	public final StreamCreator  anonymous_stream() throws RecognitionException, TokenStreamException {
		StreamCreator sc;
		
		Token  tf = null;
		Token  tp = null;
		Token  ts = null;
		Token  tl = null;
		sc = null; StreamType st = null; List params = new ArrayList();
		Statement body; List types = new ArrayList(); Type t; StreamSpec ss = null;
		List p = null; int sst = 0; FEContext ctx = null;
		
		try {      // for error handling
			{
			switch ( LA(1)) {
			case TK_portal:
			case TK_boolean:
			case TK_float:
			case TK_bit:
			case TK_int:
			case TK_void:
			case TK_double:
			case TK_complex:
			case TK_float2:
			case TK_float3:
			case TK_float4:
			case ID:
			{
				st=stream_type_decl();
				break;
			}
			case TK_filter:
			case TK_pipeline:
			case TK_splitjoin:
			case TK_feedbackloop:
			{
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			}
			{
			switch ( LA(1)) {
			case TK_filter:
			{
				tf = LT(1);
				match(TK_filter);
				ss=filter_body(getContext(tf), st, null, Collections.EMPTY_LIST);
				{
				if ((LA(1)==TK_to||LA(1)==SEMI)) {
					{
					switch ( LA(1)) {
					case TK_to:
					{
						p=portal_spec();
						break;
					}
					case SEMI:
					{
						break;
					}
					default:
					{
						throw new NoViableAltException(LT(1), getFilename());
					}
					}
					}
					match(SEMI);
				}
				else if ((_tokenSet_8.member(LA(1)))) {
				}
				else {
					throw new NoViableAltException(LT(1), getFilename());
				}
				
				}
				if ( inputState.guessing==0 ) {
					sc = new SCAnon(getContext(tf), ss, p);
				}
				break;
			}
			case TK_pipeline:
			case TK_splitjoin:
			case TK_feedbackloop:
			{
				{
				switch ( LA(1)) {
				case TK_pipeline:
				{
					tp = LT(1);
					match(TK_pipeline);
					if ( inputState.guessing==0 ) {
						ctx = getContext(tp); sst = StreamSpec.STREAM_PIPELINE;
					}
					break;
				}
				case TK_splitjoin:
				{
					ts = LT(1);
					match(TK_splitjoin);
					if ( inputState.guessing==0 ) {
						ctx = getContext(ts); sst = StreamSpec.STREAM_SPLITJOIN;
					}
					break;
				}
				case TK_feedbackloop:
				{
					tl = LT(1);
					match(TK_feedbackloop);
					if ( inputState.guessing==0 ) {
						ctx = getContext(tl); sst = StreamSpec.STREAM_FEEDBACKLOOP;
					}
					break;
				}
				default:
				{
					throw new NoViableAltException(LT(1), getFilename());
				}
				}
				}
				body=block();
				{
				if ((LA(1)==TK_to||LA(1)==SEMI)) {
					{
					switch ( LA(1)) {
					case TK_to:
					{
						p=portal_spec();
						break;
					}
					case SEMI:
					{
						break;
					}
					default:
					{
						throw new NoViableAltException(LT(1), getFilename());
					}
					}
					}
					match(SEMI);
				}
				else if ((_tokenSet_8.member(LA(1)))) {
				}
				else {
					throw new NoViableAltException(LT(1), getFilename());
				}
				
				}
				if ( inputState.guessing==0 ) {
					sc = new SCAnon(ctx, sst, body, p);
				}
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_8);
			} else {
			  throw ex;
			}
		}
		return sc;
	}
	
	public final StreamCreator  named_stream() throws RecognitionException, TokenStreamException {
		StreamCreator sc;
		
		Token  id = null;
		sc = null; List params = new ArrayList(); List types = new ArrayList();
		Type t; List p = null;
		
		try {      // for error handling
			id = LT(1);
			match(ID);
			{
			switch ( LA(1)) {
			case LESS_THAN:
			{
				match(LESS_THAN);
				t=data_type();
				match(MORE_THAN);
				if ( inputState.guessing==0 ) {
					types.add(t);
				}
				break;
			}
			case TK_to:
			case LPAREN:
			case SEMI:
			{
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			}
			{
			switch ( LA(1)) {
			case LPAREN:
			{
				params=func_call_params();
				break;
			}
			case TK_to:
			case SEMI:
			{
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			}
			{
			switch ( LA(1)) {
			case TK_to:
			{
				p=portal_spec();
				break;
			}
			case SEMI:
			{
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			}
			if ( inputState.guessing==0 ) {
				sc = new SCSimple(getContext(id), id.getText(), types, params, p);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_4);
			} else {
			  throw ex;
			}
		}
		return sc;
	}
	
	public final List  portal_spec() throws RecognitionException, TokenStreamException {
		List p;
		
		Token  id = null;
		Token  id2 = null;
		p = new ArrayList(); Expression pn;
		
		try {      // for error handling
			match(TK_to);
			id = LT(1);
			match(ID);
			if ( inputState.guessing==0 ) {
				p.add(new ExprVar(getContext(id), id.getText()));
			}
			{
			_loop64:
			do {
				if ((LA(1)==COMMA)) {
					match(COMMA);
					id2 = LT(1);
					match(ID);
					if ( inputState.guessing==0 ) {
						p.add(new ExprVar(getContext(id2), id2.getText()));
					}
				}
				else {
					break _loop64;
				}
				
			} while (true);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_4);
			} else {
			  throw ex;
			}
		}
		return p;
	}
	
	public final SplitterJoiner  splitter_or_joiner() throws RecognitionException, TokenStreamException {
		SplitterJoiner sj;
		
		Token  tr = null;
		Token  td = null;
		sj = null; Expression x; List l;
		
		try {      // for error handling
			switch ( LA(1)) {
			case TK_roundrobin:
			{
				tr = LT(1);
				match(TK_roundrobin);
				{
				boolean synPredMatched82 = false;
				if (((LA(1)==LPAREN))) {
					int _m82 = mark();
					synPredMatched82 = true;
					inputState.guessing++;
					try {
						{
						match(LPAREN);
						match(RPAREN);
						}
					}
					catch (RecognitionException pe) {
						synPredMatched82 = false;
					}
					rewind(_m82);
inputState.guessing--;
				}
				if ( synPredMatched82 ) {
					match(LPAREN);
					match(RPAREN);
					if ( inputState.guessing==0 ) {
						sj = new SJRoundRobin(getContext(tr));
					}
				}
				else {
					boolean synPredMatched84 = false;
					if (((LA(1)==LPAREN))) {
						int _m84 = mark();
						synPredMatched84 = true;
						inputState.guessing++;
						try {
							{
							match(LPAREN);
							right_expr();
							match(RPAREN);
							}
						}
						catch (RecognitionException pe) {
							synPredMatched84 = false;
						}
						rewind(_m84);
inputState.guessing--;
					}
					if ( synPredMatched84 ) {
						match(LPAREN);
						x=right_expr();
						match(RPAREN);
						if ( inputState.guessing==0 ) {
							sj = new SJRoundRobin(getContext(tr), x);
						}
					}
					else if ((LA(1)==LPAREN)) {
						l=func_call_params();
						if ( inputState.guessing==0 ) {
							sj = new SJWeightedRR(getContext(tr), l);
						}
					}
					else if ((LA(1)==SEMI)) {
						if ( inputState.guessing==0 ) {
							sj = new SJRoundRobin(getContext(tr));
						}
					}
					else {
						throw new NoViableAltException(LT(1), getFilename());
					}
					}
					}
					break;
				}
				case TK_duplicate:
				{
					td = LT(1);
					match(TK_duplicate);
					{
					switch ( LA(1)) {
					case LPAREN:
					{
						match(LPAREN);
						match(RPAREN);
						break;
					}
					case SEMI:
					{
						break;
					}
					default:
					{
						throw new NoViableAltException(LT(1), getFilename());
					}
					}
					}
					if ( inputState.guessing==0 ) {
						sj = new SJDuplicate(getContext(td));
					}
					break;
				}
				default:
				{
					throw new NoViableAltException(LT(1), getFilename());
				}
				}
			}
			catch (RecognitionException ex) {
				if (inputState.guessing==0) {
					reportError(ex);
					recover(ex,_tokenSet_4);
				} else {
				  throw ex;
				}
			}
			return sj;
		}
		
	public final Type  primitive_type() throws RecognitionException, TokenStreamException {
		Type t;
		
		t = null;
		
		try {      // for error handling
			switch ( LA(1)) {
			case TK_boolean:
			{
				match(TK_boolean);
				if ( inputState.guessing==0 ) {
					t = new TypePrimitive(TypePrimitive.TYPE_BOOLEAN);
				}
				break;
			}
			case TK_bit:
			{
				match(TK_bit);
				if ( inputState.guessing==0 ) {
					t = new TypePrimitive(TypePrimitive.TYPE_BIT);
				}
				break;
			}
			case TK_int:
			{
				match(TK_int);
				if ( inputState.guessing==0 ) {
					t = new TypePrimitive(TypePrimitive.TYPE_INT);
				}
				break;
			}
			case TK_float:
			{
				match(TK_float);
				if ( inputState.guessing==0 ) {
					t = new TypePrimitive(TypePrimitive.TYPE_FLOAT);
				}
				break;
			}
			case TK_double:
			{
				match(TK_double);
				if ( inputState.guessing==0 ) {
					t =  new TypePrimitive(TypePrimitive.TYPE_DOUBLE);
				}
				break;
			}
			case TK_complex:
			{
				match(TK_complex);
				if ( inputState.guessing==0 ) {
					t = new TypePrimitive(TypePrimitive.TYPE_COMPLEX);
				}
				break;
			}
			case TK_float2:
			{
				match(TK_float2);
				if ( inputState.guessing==0 ) {
					t = new TypePrimitive(TypePrimitive.TYPE_FLOAT2);
				}
				break;
			}
			case TK_float3:
			{
				match(TK_float3);
				if ( inputState.guessing==0 ) {
					t = new TypePrimitive(TypePrimitive.TYPE_FLOAT3);
				}
				break;
			}
			case TK_float4:
			{
				match(TK_float4);
				if ( inputState.guessing==0 ) {
					t = new TypePrimitive(TypePrimitive.TYPE_FLOAT4);
				}
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_20);
			} else {
			  throw ex;
			}
		}
		return t;
	}
	
	public final Parameter  param_decl() throws RecognitionException, TokenStreamException {
		Parameter p;
		
		Token  id = null;
		Type t; p = null;
		
		try {      // for error handling
			t=data_type();
			id = LT(1);
			match(ID);
			if ( inputState.guessing==0 ) {
				p = new Parameter(t, id.getText());
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_21);
			} else {
			  throw ex;
			}
		}
		return p;
	}
	
	public final Statement  for_init_statement() throws RecognitionException, TokenStreamException {
		Statement s;
		
		s = null;
		
		try {      // for error handling
			boolean synPredMatched123 = false;
			if (((_tokenSet_1.member(LA(1))))) {
				int _m123 = mark();
				synPredMatched123 = true;
				inputState.guessing++;
				try {
					{
					variable_decl();
					}
				}
				catch (RecognitionException pe) {
					synPredMatched123 = false;
				}
				rewind(_m123);
inputState.guessing--;
			}
			if ( synPredMatched123 ) {
				s=variable_decl();
			}
			else {
				boolean synPredMatched125 = false;
				if (((_tokenSet_6.member(LA(1))))) {
					int _m125 = mark();
					synPredMatched125 = true;
					inputState.guessing++;
					try {
						{
						expr_statement();
						}
					}
					catch (RecognitionException pe) {
						synPredMatched125 = false;
					}
					rewind(_m125);
inputState.guessing--;
				}
				if ( synPredMatched125 ) {
					s=expr_statement();
				}
				else {
					boolean synPredMatched127 = false;
					if (((LA(1)==SEMI))) {
						int _m127 = mark();
						synPredMatched127 = true;
						inputState.guessing++;
						try {
							{
							match(SEMI);
							}
						}
						catch (RecognitionException pe) {
							synPredMatched127 = false;
						}
						rewind(_m127);
inputState.guessing--;
					}
					if ( synPredMatched127 ) {
						if ( inputState.guessing==0 ) {
							s = new StmtEmpty(null);
						}
					}
					else {
						throw new NoViableAltException(LT(1), getFilename());
					}
					}}
				}
				catch (RecognitionException ex) {
					if (inputState.guessing==0) {
						reportError(ex);
						recover(ex,_tokenSet_4);
					} else {
					  throw ex;
					}
				}
				return s;
			}
			
	public final Statement  for_incr_statement() throws RecognitionException, TokenStreamException {
		Statement s;
		
		s = null;
		
		try {      // for error handling
			switch ( LA(1)) {
			case TK_peek:
			case TK_pop:
			case INCREMENT:
			case DECREMENT:
			case ID:
			{
				s=expr_statement();
				break;
			}
			case RPAREN:
			{
				if ( inputState.guessing==0 ) {
					s = new StmtEmpty(null);
				}
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_22);
			} else {
			  throw ex;
			}
		}
		return s;
	}
	
	public final Expression  incOrDec() throws RecognitionException, TokenStreamException {
		Expression x;
		
		Token  i = null;
		Token  d = null;
		x = null;
		
		try {      // for error handling
			switch ( LA(1)) {
			case ID:
			{
				x=left_expr();
				{
				switch ( LA(1)) {
				case INCREMENT:
				{
					match(INCREMENT);
					if ( inputState.guessing==0 ) {
						x = new ExprUnary(x.getContext(), ExprUnary.UNOP_POSTINC, x);
					}
					break;
				}
				case DECREMENT:
				{
					match(DECREMENT);
					if ( inputState.guessing==0 ) {
						x = new ExprUnary(x.getContext(), ExprUnary.UNOP_POSTDEC, x);
					}
					break;
				}
				default:
				{
					throw new NoViableAltException(LT(1), getFilename());
				}
				}
				}
				break;
			}
			case INCREMENT:
			{
				i = LT(1);
				match(INCREMENT);
				x=left_expr();
				if ( inputState.guessing==0 ) {
					x = new ExprUnary(getContext(i), ExprUnary.UNOP_PREINC, x);
				}
				break;
			}
			case DECREMENT:
			{
				d = LT(1);
				match(DECREMENT);
				x=left_expr();
				if ( inputState.guessing==0 ) {
					x = new ExprUnary(getContext(d), ExprUnary.UNOP_PREDEC, x);
				}
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_23);
			} else {
			  throw ex;
			}
		}
		return x;
	}
	
	public final Expression  left_expr() throws RecognitionException, TokenStreamException {
		Expression x;
		
		x = null;
		
		try {      // for error handling
			x=value();
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_24);
			} else {
			  throw ex;
			}
		}
		return x;
	}
	
	public final Statement  assign_expr() throws RecognitionException, TokenStreamException {
		Statement s;
		
		s = null; Expression l, r; int o = 0;
		
		try {      // for error handling
			l=left_expr();
			{
			switch ( LA(1)) {
			case ASSIGN:
			{
				match(ASSIGN);
				if ( inputState.guessing==0 ) {
					o = 0;
				}
				break;
			}
			case PLUS_EQUALS:
			{
				match(PLUS_EQUALS);
				if ( inputState.guessing==0 ) {
					o = ExprBinary.BINOP_ADD;
				}
				break;
			}
			case MINUS_EQUALS:
			{
				match(MINUS_EQUALS);
				if ( inputState.guessing==0 ) {
					o = ExprBinary.BINOP_SUB;
				}
				break;
			}
			case STAR_EQUALS:
			{
				match(STAR_EQUALS);
				if ( inputState.guessing==0 ) {
					o = ExprBinary.BINOP_MUL;
				}
				break;
			}
			case DIV_EQUALS:
			{
				match(DIV_EQUALS);
				if ( inputState.guessing==0 ) {
					o = ExprBinary.BINOP_DIV;
				}
				break;
			}
			case LSHIFT_EQUALS:
			{
				match(LSHIFT_EQUALS);
				if ( inputState.guessing==0 ) {
					o = ExprBinary.BINOP_LSHIFT;
				}
				break;
			}
			case RSHIFT_EQUALS:
			{
				match(RSHIFT_EQUALS);
				if ( inputState.guessing==0 ) {
					o = ExprBinary.BINOP_RSHIFT;
				}
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			}
			r=right_expr();
			if ( inputState.guessing==0 ) {
				s = new StmtAssign(l.getContext(), l, r, o);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_7);
			} else {
			  throw ex;
			}
		}
		return s;
	}
	
	public final Expression  func_call() throws RecognitionException, TokenStreamException {
		Expression x;
		
		Token  name = null;
		x = null; List l;
		
		try {      // for error handling
			name = LT(1);
			match(ID);
			l=func_call_params();
			if ( inputState.guessing==0 ) {
				x = new ExprFunCall(getContext(name), name.getText(), l);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_23);
			} else {
			  throw ex;
			}
		}
		return x;
	}
	
	public final Expression  streamit_value_expr() throws RecognitionException, TokenStreamException {
		Expression x;
		
		Token  t = null;
		Token  u = null;
		x = null;
		
		try {      // for error handling
			switch ( LA(1)) {
			case TK_pop:
			{
				t = LT(1);
				match(TK_pop);
				match(LPAREN);
				match(RPAREN);
				if ( inputState.guessing==0 ) {
					x = new ExprPop(getContext(t));
				}
				break;
			}
			case TK_peek:
			{
				u = LT(1);
				match(TK_peek);
				match(LPAREN);
				x=right_expr();
				match(RPAREN);
				if ( inputState.guessing==0 ) {
					x = new ExprPeek(getContext(u), x);
				}
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_23);
			} else {
			  throw ex;
			}
		}
		return x;
	}
	
	public final Expression  helper_call() throws RecognitionException, TokenStreamException {
		Expression x;
		
		Token  p = null;
		Token  m = null;
		x = null; List l;
		
		try {      // for error handling
			p = LT(1);
			match(ID);
			match(DOT);
			m = LT(1);
			match(ID);
			l=func_call_params();
			if ( inputState.guessing==0 ) {
				x = new ExprHelperCall(getContext(p), p.getText(), m.getText(), l);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_23);
			} else {
			  throw ex;
			}
		}
		return x;
	}
	
	public final Expression  value() throws RecognitionException, TokenStreamException {
		Expression x;
		
		Token  name = null;
		Token  field = null;
		Token  l = null;
		x = null; Expression array;
		
		try {      // for error handling
			name = LT(1);
			match(ID);
			if ( inputState.guessing==0 ) {
				x = new ExprVar(getContext(name), name.getText());
			}
			{
			_loop204:
			do {
				switch ( LA(1)) {
				case DOT:
				{
					match(DOT);
					field = LT(1);
					match(ID);
					if ( inputState.guessing==0 ) {
						x = new ExprField(x.getContext(), x, field.getText());
					}
					break;
				}
				case LSQUARE:
				{
					l = LT(1);
					match(LSQUARE);
					{
					switch ( LA(1)) {
					case TK_peek:
					case TK_pop:
					case TK_float2:
					case TK_float3:
					case TK_float4:
					case TK_pi:
					case TK_true:
					case TK_false:
					case LPAREN:
					case INCREMENT:
					case MINUS:
					case DECREMENT:
					case BITWISE_COMPLEMENT:
					case BANG:
					case CHAR_LITERAL:
					case STRING_LITERAL:
					case HEXNUMBER:
					case NUMBER:
					case ID:
					{
						array=right_expr();
						if ( inputState.guessing==0 ) {
							x = new ExprArray(x.getContext(), x, array);
						}
						break;
					}
					case RSQUARE:
					{
						if ( inputState.guessing==0 ) {
							throw new SemanticException("missing array index",
													getFilename(), l.getLine());
						}
						break;
					}
					default:
					{
						throw new NoViableAltException(LT(1), getFilename());
					}
					}
					}
					match(RSQUARE);
					break;
				}
				default:
				{
					break _loop204;
				}
				}
			} while (true);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_24);
			} else {
			  throw ex;
			}
		}
		return x;
	}
	
	public final Expression  ternaryExpr() throws RecognitionException, TokenStreamException {
		Expression x;
		
		x = null; Expression b, c;
		
		try {      // for error handling
			x=logicOrExpr();
			{
			switch ( LA(1)) {
			case QUESTION:
			{
				match(QUESTION);
				b=ternaryExpr();
				match(COLON);
				c=ternaryExpr();
				if ( inputState.guessing==0 ) {
					x = new ExprTernary(x.getContext(), ExprTernary.TEROP_COND,
										x, b, c);
				}
				break;
			}
			case TK_peek:
			case TK_pop:
			case TK_push:
			case RPAREN:
			case LCURLY:
			case RCURLY:
			case RSQUARE:
			case COLON:
			case SEMI:
			case COMMA:
			{
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_16);
			} else {
			  throw ex;
			}
		}
		return x;
	}
	
	public final Expression  float2_initializer() throws RecognitionException, TokenStreamException {
		Expression x;
		
		Token  lc = null;
		x = null; Expression p1, p2;
		
		try {      // for error handling
			lc = LT(1);
			match(TK_float2);
			match(LPAREN);
			p1=right_expr();
			match(COMMA);
			p2=right_expr();
			match(RPAREN);
			if ( inputState.guessing==0 ) {
				x = new ExprComposite(getContext(lc), p1, p2, null, null);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_16);
			} else {
			  throw ex;
			}
		}
		return x;
	}
	
	public final Expression  float3_initializer() throws RecognitionException, TokenStreamException {
		Expression x;
		
		Token  lc = null;
		x = null; Expression p1, p2, p3;
		
		try {      // for error handling
			lc = LT(1);
			match(TK_float3);
			match(LPAREN);
			p1=right_expr();
			match(COMMA);
			p2=right_expr();
			match(COMMA);
			p3=right_expr();
			match(RPAREN);
			if ( inputState.guessing==0 ) {
				x = new ExprComposite(getContext(lc), p1, p2, p3, null);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_16);
			} else {
			  throw ex;
			}
		}
		return x;
	}
	
	public final Expression  float4_initializer() throws RecognitionException, TokenStreamException {
		Expression x;
		
		Token  lc = null;
		x = null; Expression p1, p2, p3, p4;
		
		try {      // for error handling
			lc = LT(1);
			match(TK_float4);
			match(LPAREN);
			p1=right_expr();
			match(COMMA);
			p2=right_expr();
			match(COMMA);
			p3=right_expr();
			match(COMMA);
			p4=right_expr();
			match(RPAREN);
			if ( inputState.guessing==0 ) {
				x = new ExprComposite(getContext(lc), p1, p2, p3, p4);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_16);
			} else {
			  throw ex;
			}
		}
		return x;
	}
	
	public final Expression  arr_initializer() throws RecognitionException, TokenStreamException {
		Expression x;
		
		Token  lc = null;
		ArrayList l = new ArrayList(); 
		x = null;
		Expression y;
		
		try {      // for error handling
			lc = LT(1);
			match(LCURLY);
			{
			switch ( LA(1)) {
			case TK_peek:
			case TK_pop:
			case TK_float2:
			case TK_float3:
			case TK_float4:
			case TK_pi:
			case TK_true:
			case TK_false:
			case LPAREN:
			case LCURLY:
			case INCREMENT:
			case MINUS:
			case DECREMENT:
			case BITWISE_COMPLEMENT:
			case BANG:
			case CHAR_LITERAL:
			case STRING_LITERAL:
			case HEXNUMBER:
			case NUMBER:
			case ID:
			{
				y=var_initializer();
				if ( inputState.guessing==0 ) {
					l.add(y);
				}
				{
				_loop154:
				do {
					if ((LA(1)==COMMA)) {
						match(COMMA);
						y=var_initializer();
						if ( inputState.guessing==0 ) {
							l.add(y);
						}
					}
					else {
						break _loop154;
					}
					
				} while (true);
				}
				break;
			}
			case RCURLY:
			{
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			}
			match(RCURLY);
			if ( inputState.guessing==0 ) {
				x = new ExprArrayInit(getContext(lc), l);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_12);
			} else {
			  throw ex;
			}
		}
		return x;
	}
	
	public final Expression  logicOrExpr() throws RecognitionException, TokenStreamException {
		Expression x;
		
		x = null; Expression r; int o = 0;
		
		try {      // for error handling
			x=logicAndExpr();
			{
			_loop159:
			do {
				if ((LA(1)==LOGIC_OR)) {
					match(LOGIC_OR);
					r=logicAndExpr();
					if ( inputState.guessing==0 ) {
						x = new ExprBinary(x.getContext(), ExprBinary.BINOP_OR, x, r);
					}
				}
				else {
					break _loop159;
				}
				
			} while (true);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_25);
			} else {
			  throw ex;
			}
		}
		return x;
	}
	
	public final Expression  logicAndExpr() throws RecognitionException, TokenStreamException {
		Expression x;
		
		x = null; Expression r;
		
		try {      // for error handling
			x=bitwiseExpr();
			{
			_loop162:
			do {
				if ((LA(1)==LOGIC_AND)) {
					match(LOGIC_AND);
					r=bitwiseExpr();
					if ( inputState.guessing==0 ) {
						x = new ExprBinary(x.getContext(), ExprBinary.BINOP_AND, x, r);
					}
				}
				else {
					break _loop162;
				}
				
			} while (true);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_26);
			} else {
			  throw ex;
			}
		}
		return x;
	}
	
	public final Expression  bitwiseExpr() throws RecognitionException, TokenStreamException {
		Expression x;
		
		x = null; Expression r; int o = 0;
		
		try {      // for error handling
			x=equalExpr();
			{
			_loop166:
			do {
				if (((LA(1) >= BITWISE_AND && LA(1) <= BITWISE_XOR))) {
					{
					switch ( LA(1)) {
					case BITWISE_OR:
					{
						match(BITWISE_OR);
						if ( inputState.guessing==0 ) {
							o = ExprBinary.BINOP_BOR;
						}
						break;
					}
					case BITWISE_AND:
					{
						match(BITWISE_AND);
						if ( inputState.guessing==0 ) {
							o = ExprBinary.BINOP_BAND;
						}
						break;
					}
					case BITWISE_XOR:
					{
						match(BITWISE_XOR);
						if ( inputState.guessing==0 ) {
							o = ExprBinary.BINOP_BXOR;
						}
						break;
					}
					default:
					{
						throw new NoViableAltException(LT(1), getFilename());
					}
					}
					}
					r=equalExpr();
					if ( inputState.guessing==0 ) {
						x = new ExprBinary(x.getContext(), o, x, r);
					}
				}
				else {
					break _loop166;
				}
				
			} while (true);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_27);
			} else {
			  throw ex;
			}
		}
		return x;
	}
	
	public final Expression  equalExpr() throws RecognitionException, TokenStreamException {
		Expression x;
		
		x = null; Expression r; int o = 0;
		
		try {      // for error handling
			x=compareExpr();
			{
			_loop170:
			do {
				if ((LA(1)==EQUAL||LA(1)==NOT_EQUAL)) {
					{
					switch ( LA(1)) {
					case EQUAL:
					{
						match(EQUAL);
						if ( inputState.guessing==0 ) {
							o = ExprBinary.BINOP_EQ;
						}
						break;
					}
					case NOT_EQUAL:
					{
						match(NOT_EQUAL);
						if ( inputState.guessing==0 ) {
							o = ExprBinary.BINOP_NEQ;
						}
						break;
					}
					default:
					{
						throw new NoViableAltException(LT(1), getFilename());
					}
					}
					}
					r=compareExpr();
					if ( inputState.guessing==0 ) {
						x = new ExprBinary(x.getContext(), o, x, r);
					}
				}
				else {
					break _loop170;
				}
				
			} while (true);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_28);
			} else {
			  throw ex;
			}
		}
		return x;
	}
	
	public final Expression  compareExpr() throws RecognitionException, TokenStreamException {
		Expression x;
		
		x = null; Expression r; int o = 0;
		
		try {      // for error handling
			x=addExpr();
			{
			_loop174:
			do {
				if (((LA(1) >= LESS_THAN && LA(1) <= MORE_EQUAL))) {
					{
					switch ( LA(1)) {
					case LESS_THAN:
					{
						match(LESS_THAN);
						if ( inputState.guessing==0 ) {
							o = ExprBinary.BINOP_LT;
						}
						break;
					}
					case LESS_EQUAL:
					{
						match(LESS_EQUAL);
						if ( inputState.guessing==0 ) {
							o = ExprBinary.BINOP_LE;
						}
						break;
					}
					case MORE_THAN:
					{
						match(MORE_THAN);
						if ( inputState.guessing==0 ) {
							o = ExprBinary.BINOP_GT;
						}
						break;
					}
					case MORE_EQUAL:
					{
						match(MORE_EQUAL);
						if ( inputState.guessing==0 ) {
							o = ExprBinary.BINOP_GE;
						}
						break;
					}
					default:
					{
						throw new NoViableAltException(LT(1), getFilename());
					}
					}
					}
					r=addExpr();
					if ( inputState.guessing==0 ) {
						x = new ExprBinary(x.getContext(), o, x, r);
					}
				}
				else {
					break _loop174;
				}
				
			} while (true);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_29);
			} else {
			  throw ex;
			}
		}
		return x;
	}
	
	public final Expression  addExpr() throws RecognitionException, TokenStreamException {
		Expression x;
		
		x = null; Expression r; int o = 0;
		
		try {      // for error handling
			x=multExpr();
			{
			_loop178:
			do {
				if ((LA(1)==PLUS||LA(1)==MINUS)) {
					{
					switch ( LA(1)) {
					case PLUS:
					{
						match(PLUS);
						if ( inputState.guessing==0 ) {
							o = ExprBinary.BINOP_ADD;
						}
						break;
					}
					case MINUS:
					{
						match(MINUS);
						if ( inputState.guessing==0 ) {
							o = ExprBinary.BINOP_SUB;
						}
						break;
					}
					default:
					{
						throw new NoViableAltException(LT(1), getFilename());
					}
					}
					}
					r=multExpr();
					if ( inputState.guessing==0 ) {
						x = new ExprBinary(x.getContext(), o, x, r);
					}
				}
				else {
					break _loop178;
				}
				
			} while (true);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_30);
			} else {
			  throw ex;
			}
		}
		return x;
	}
	
	public final Expression  multExpr() throws RecognitionException, TokenStreamException {
		Expression x;
		
		x = null; Expression r; int o = 0;
		
		try {      // for error handling
			x=castExpr();
			{
			_loop182:
			do {
				if ((_tokenSet_31.member(LA(1)))) {
					{
					switch ( LA(1)) {
					case STAR:
					{
						match(STAR);
						if ( inputState.guessing==0 ) {
							o = ExprBinary.BINOP_MUL;
						}
						break;
					}
					case DIV:
					{
						match(DIV);
						if ( inputState.guessing==0 ) {
							o = ExprBinary.BINOP_DIV;
						}
						break;
					}
					case MOD:
					{
						match(MOD);
						if ( inputState.guessing==0 ) {
							o = ExprBinary.BINOP_MOD;
						}
						break;
					}
					case LSHIFT:
					{
						match(LSHIFT);
						if ( inputState.guessing==0 ) {
							o = ExprBinary.BINOP_LSHIFT;
						}
						break;
					}
					case RSHIFT:
					{
						match(RSHIFT);
						if ( inputState.guessing==0 ) {
							o = ExprBinary.BINOP_RSHIFT;
						}
						break;
					}
					default:
					{
						throw new NoViableAltException(LT(1), getFilename());
					}
					}
					}
					r=castExpr();
					if ( inputState.guessing==0 ) {
						x = new ExprBinary(x.getContext(), o, x, r);
					}
				}
				else {
					break _loop182;
				}
				
			} while (true);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_32);
			} else {
			  throw ex;
			}
		}
		return x;
	}
	
	public final Expression  castExpr() throws RecognitionException, TokenStreamException {
		Expression x;
		
		Token  l = null;
		x = null; Type t=null;
		
		try {      // for error handling
			boolean synPredMatched185 = false;
			if (((LA(1)==LPAREN))) {
				int _m185 = mark();
				synPredMatched185 = true;
				inputState.guessing++;
				try {
					{
					match(LPAREN);
					primitive_type();
					}
				}
				catch (RecognitionException pe) {
					synPredMatched185 = false;
				}
				rewind(_m185);
inputState.guessing--;
			}
			if ( synPredMatched185 ) {
				{
				l = LT(1);
				match(LPAREN);
				t=primitive_type();
				match(RPAREN);
				}
				x=inc_dec_expr();
				if ( inputState.guessing==0 ) {
					x = new ExprTypeCast(getContext(l), t, x);
				}
			}
			else if ((_tokenSet_33.member(LA(1)))) {
				x=inc_dec_expr();
			}
			else {
				throw new NoViableAltException(LT(1), getFilename());
			}
			
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_23);
			} else {
			  throw ex;
			}
		}
		return x;
	}
	
	public final Expression  inc_dec_expr() throws RecognitionException, TokenStreamException {
		Expression x;
		
		Token  b = null;
		x = null;
		
		try {      // for error handling
			boolean synPredMatched189 = false;
			if (((LA(1)==INCREMENT||LA(1)==DECREMENT||LA(1)==ID))) {
				int _m189 = mark();
				synPredMatched189 = true;
				inputState.guessing++;
				try {
					{
					incOrDec();
					}
				}
				catch (RecognitionException pe) {
					synPredMatched189 = false;
				}
				rewind(_m189);
inputState.guessing--;
			}
			if ( synPredMatched189 ) {
				x=incOrDec();
			}
			else if ((LA(1)==BANG)) {
				b = LT(1);
				match(BANG);
				x=value_expr();
				if ( inputState.guessing==0 ) {
					x = new ExprUnary(getContext(b), ExprUnary.UNOP_NOT, x);
				}
			}
			else if ((_tokenSet_34.member(LA(1)))) {
				x=value_expr();
			}
			else {
				throw new NoViableAltException(LT(1), getFilename());
			}
			
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_23);
			} else {
			  throw ex;
			}
		}
		return x;
	}
	
	public final Expression  value_expr() throws RecognitionException, TokenStreamException {
		Expression x;
		
		Token  m = null;
		Token  c = null;
		x = null; boolean neg = false;
		
		try {      // for error handling
			switch ( LA(1)) {
			case TK_peek:
			case TK_pop:
			case TK_pi:
			case TK_true:
			case TK_false:
			case LPAREN:
			case MINUS:
			case CHAR_LITERAL:
			case STRING_LITERAL:
			case HEXNUMBER:
			case NUMBER:
			case ID:
			{
				{
				switch ( LA(1)) {
				case MINUS:
				{
					m = LT(1);
					match(MINUS);
					if ( inputState.guessing==0 ) {
						neg = true;
					}
					break;
				}
				case TK_peek:
				case TK_pop:
				case TK_pi:
				case TK_true:
				case TK_false:
				case LPAREN:
				case CHAR_LITERAL:
				case STRING_LITERAL:
				case HEXNUMBER:
				case NUMBER:
				case ID:
				{
					break;
				}
				default:
				{
					throw new NoViableAltException(LT(1), getFilename());
				}
				}
				}
				{
				switch ( LA(1)) {
				case TK_pi:
				case TK_true:
				case TK_false:
				case LPAREN:
				case CHAR_LITERAL:
				case STRING_LITERAL:
				case HEXNUMBER:
				case NUMBER:
				case ID:
				{
					x=minic_value_expr();
					break;
				}
				case TK_peek:
				case TK_pop:
				{
					x=streamit_value_expr();
					break;
				}
				default:
				{
					throw new NoViableAltException(LT(1), getFilename());
				}
				}
				}
				if ( inputState.guessing==0 ) {
					if (neg) x = new ExprUnary(getContext(m), ExprUnary.UNOP_NEG, x);
				}
				break;
			}
			case BITWISE_COMPLEMENT:
			{
				c = LT(1);
				match(BITWISE_COMPLEMENT);
				x=value_expr();
				if ( inputState.guessing==0 ) {
					x = new ExprUnary(getContext(c), ExprUnary.UNOP_COMPLEMENT, x);
				}
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_23);
			} else {
			  throw ex;
			}
		}
		return x;
	}
	
	public final Expression  minic_value_expr() throws RecognitionException, TokenStreamException {
		Expression x;
		
		x = null;
		
		try {      // for error handling
			switch ( LA(1)) {
			case LPAREN:
			{
				match(LPAREN);
				x=right_expr();
				match(RPAREN);
				break;
			}
			case TK_pi:
			case TK_true:
			case TK_false:
			case CHAR_LITERAL:
			case STRING_LITERAL:
			case HEXNUMBER:
			case NUMBER:
			{
				x=constantExpr();
				break;
			}
			default:
				boolean synPredMatched198 = false;
				if (((LA(1)==ID))) {
					int _m198 = mark();
					synPredMatched198 = true;
					inputState.guessing++;
					try {
						{
						func_call();
						}
					}
					catch (RecognitionException pe) {
						synPredMatched198 = false;
					}
					rewind(_m198);
inputState.guessing--;
				}
				if ( synPredMatched198 ) {
					x=func_call();
				}
				else {
					boolean synPredMatched200 = false;
					if (((LA(1)==ID))) {
						int _m200 = mark();
						synPredMatched200 = true;
						inputState.guessing++;
						try {
							{
							helper_call();
							}
						}
						catch (RecognitionException pe) {
							synPredMatched200 = false;
						}
						rewind(_m200);
inputState.guessing--;
					}
					if ( synPredMatched200 ) {
						x=helper_call();
					}
					else if ((LA(1)==ID)) {
						x=value();
					}
				else {
					throw new NoViableAltException(LT(1), getFilename());
				}
				}}
			}
			catch (RecognitionException ex) {
				if (inputState.guessing==0) {
					reportError(ex);
					recover(ex,_tokenSet_23);
				} else {
				  throw ex;
				}
			}
			return x;
		}
		
	public final Expression  constantExpr() throws RecognitionException, TokenStreamException {
		Expression x;
		
		Token  h = null;
		Token  n = null;
		Token  c = null;
		Token  s = null;
		Token  pi = null;
		Token  t = null;
		Token  f = null;
		x = null;
		
		try {      // for error handling
			switch ( LA(1)) {
			case HEXNUMBER:
			{
				h = LT(1);
				match(HEXNUMBER);
				if ( inputState.guessing==0 ) {
					String tmp = h.getText().substring(2);
								   Integer iti = new Integer(
						 (int ) ( ( Long.parseLong(tmp, 16) - (long) Integer.MIN_VALUE ) 
							  % ( (long)Integer.MAX_VALUE - (long) Integer.MIN_VALUE + 1) 
							  + Integer.MIN_VALUE) );
									x = ExprConstant.createConstant(getContext(h), iti.toString() );
				}
				break;
			}
			case NUMBER:
			{
				n = LT(1);
				match(NUMBER);
				if ( inputState.guessing==0 ) {
					x = ExprConstant.createConstant(getContext(n), n.getText());
				}
				break;
			}
			case CHAR_LITERAL:
			{
				c = LT(1);
				match(CHAR_LITERAL);
				if ( inputState.guessing==0 ) {
					x = new ExprConstChar(getContext(c), c.getText());
				}
				break;
			}
			case STRING_LITERAL:
			{
				s = LT(1);
				match(STRING_LITERAL);
				if ( inputState.guessing==0 ) {
					x = new ExprConstStr(getContext(s), s.getText());
				}
				break;
			}
			case TK_pi:
			{
				pi = LT(1);
				match(TK_pi);
				if ( inputState.guessing==0 ) {
					x = new ExprConstFloat(getContext(pi), Math.PI);
				}
				break;
			}
			case TK_true:
			{
				t = LT(1);
				match(TK_true);
				if ( inputState.guessing==0 ) {
					x = new ExprConstBoolean(getContext(t), true);
				}
				break;
			}
			case TK_false:
			{
				f = LT(1);
				match(TK_false);
				if ( inputState.guessing==0 ) {
					x = new ExprConstBoolean(getContext(f), false);
				}
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_23);
			} else {
			  throw ex;
			}
		}
		return x;
	}
	
	public final Function  native_function_decl() throws RecognitionException, TokenStreamException {
		Function f;
		
		Token  id = null;
		Type t; List l; Statement s; f = null;
		int cls = Function.FUNC_NATIVE;
		
		try {      // for error handling
			t=data_type();
			id = LT(1);
			match(ID);
			l=param_decl_list();
			match(SEMI);
			if ( inputState.guessing==0 ) {
				f = new Function(getContext(id), cls, id.getText(), t, l, null, null, null, null);
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_35);
			} else {
			  throw ex;
			}
		}
		return f;
	}
	
	
	public static final String[] _tokenNames = {
		"<0>",
		"EOF",
		"<2>",
		"NULL_TREE_LOOKAHEAD",
		"\"filter\"",
		"\"pipeline\"",
		"\"splitjoin\"",
		"\"feedbackloop\"",
		"\"portal\"",
		"\"to\"",
		"\"handler\"",
		"\"add\"",
		"\"split\"",
		"\"join\"",
		"\"duplicate\"",
		"\"roundrobin\"",
		"\"body\"",
		"\"loop\"",
		"\"enqueue\"",
		"\"init\"",
		"\"prework\"",
		"\"work\"",
		"\"peek\"",
		"\"pop\"",
		"\"push\"",
		"\"boolean\"",
		"\"float\"",
		"\"bit\"",
		"\"int\"",
		"\"void\"",
		"\"double\"",
		"\"complex\"",
		"\"float2\"",
		"\"float3\"",
		"\"float4\"",
		"\"struct\"",
		"\"template\"",
		"\"native\"",
		"\"static\"",
		"\"helper\"",
		"\"if\"",
		"\"else\"",
		"\"while\"",
		"\"for\"",
		"\"switch\"",
		"\"case\"",
		"\"default\"",
		"\"break\"",
		"\"continue\"",
		"\"return\"",
		"\"do\"",
		"\"pi\"",
		"\"true\"",
		"\"false\"",
		"ARROW",
		"WS",
		"SL_COMMENT",
		"ML_COMMENT",
		"LPAREN",
		"RPAREN",
		"LCURLY",
		"RCURLY",
		"LSQUARE",
		"RSQUARE",
		"PLUS",
		"PLUS_EQUALS",
		"INCREMENT",
		"MINUS",
		"MINUS_EQUALS",
		"DECREMENT",
		"STAR",
		"STAR_EQUALS",
		"DIV",
		"DIV_EQUALS",
		"MOD",
		"LOGIC_AND",
		"LOGIC_OR",
		"BITWISE_AND",
		"BITWISE_OR",
		"BITWISE_XOR",
		"BITWISE_COMPLEMENT",
		"LSHIFT",
		"RSHIFT",
		"LSHIFT_EQUALS",
		"RSHIFT_EQUALS",
		"ASSIGN",
		"EQUAL",
		"NOT_EQUAL",
		"LESS_THAN",
		"LESS_EQUAL",
		"MORE_THAN",
		"MORE_EQUAL",
		"QUESTION",
		"COLON",
		"SEMI",
		"COMMA",
		"DOT",
		"BANG",
		"CHAR_LITERAL",
		"STRING_LITERAL",
		"ESC",
		"DIGIT",
		"HEXNUMBER",
		"NUMBER",
		"an identifier"
	};
	
	private static final long[] mk_tokenSet_0() {
		long[] data = { 2L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_0 = new BitSet(mk_tokenSet_0());
	private static final long[] mk_tokenSet_1() {
		long[] data = { 34326184192L, 1099511627776L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_1 = new BitSet(mk_tokenSet_1());
	private static final long[] mk_tokenSet_2() {
		long[] data = { 1030758596866L, 1099511627776L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_2 = new BitSet(mk_tokenSet_2());
	private static final long[] mk_tokenSet_3() {
		long[] data = { 240L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_3 = new BitSet(mk_tokenSet_3());
	private static final long[] mk_tokenSet_4() {
		long[] data = { 0L, 1073741824L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_4 = new BitSet(mk_tokenSet_4());
	private static final long[] mk_tokenSet_5() {
		long[] data = { 2305843043543549184L, 1099511627776L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_5 = new BitSet(mk_tokenSet_5());
	private static final long[] mk_tokenSet_6() {
		long[] data = { 12582912L, 1099511627812L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_6 = new BitSet(mk_tokenSet_6());
	private static final long[] mk_tokenSet_7() {
		long[] data = { 576460752303423488L, 1073741824L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_7 = new BitSet(mk_tokenSet_7());
	private static final long[] mk_tokenSet_8() {
		long[] data = { 3460892103176304898L, 1100585369636L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_8 = new BitSet(mk_tokenSet_8());
	private static final long[] mk_tokenSet_9() {
		long[] data = { 1152921504636207104L, 1073741824L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_9 = new BitSet(mk_tokenSet_9());
	private static final long[] mk_tokenSet_10() {
		long[] data = { 3460893099608718082L, 1100585369636L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_10 = new BitSet(mk_tokenSet_10());
	private static final long[] mk_tokenSet_11() {
		long[] data = { 18014398509482224L, 1099578736640L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_11 = new BitSet(mk_tokenSet_11());
	private static final long[] mk_tokenSet_12() {
		long[] data = { 2305843009213693952L, 3221225472L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_12 = new BitSet(mk_tokenSet_12());
	private static final long[] mk_tokenSet_13() {
		long[] data = { 1155046894939355392L, 1100585369636L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_13 = new BitSet(mk_tokenSet_13());
	private static final long[] mk_tokenSet_14() {
		long[] data = { 3460893099612389122L, 1100585369636L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_14 = new BitSet(mk_tokenSet_14());
	private static final long[] mk_tokenSet_15() {
		long[] data = { 1152921504636207104L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_15 = new BitSet(mk_tokenSet_15());
	private static final long[] mk_tokenSet_16() {
		long[] data = { -5188146770701451264L, 3758096384L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_16 = new BitSet(mk_tokenSet_16());
	private static final long[] mk_tokenSet_17() {
		long[] data = { -9223372036854775808L, 2147483648L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_17 = new BitSet(mk_tokenSet_17());
	private static final long[] mk_tokenSet_18() {
		long[] data = { -576460752274062848L, 4291231049L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_18 = new BitSet(mk_tokenSet_18());
	private static final long[] mk_tokenSet_19() {
		long[] data = { 34326184432L, 1099511627776L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_19 = new BitSet(mk_tokenSet_19());
	private static final long[] mk_tokenSet_20() {
		long[] data = { 5206161169240293616L, 1099578736640L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_20 = new BitSet(mk_tokenSet_20());
	private static final long[] mk_tokenSet_21() {
		long[] data = { 576460752303423488L, 3221225472L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_21 = new BitSet(mk_tokenSet_21());
	private static final long[] mk_tokenSet_22() {
		long[] data = { 576460752303423488L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_22 = new BitSet(mk_tokenSet_22());
	private static final long[] mk_tokenSet_23() {
		long[] data = { -5188146770701451264L, 4291231049L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_23 = new BitSet(mk_tokenSet_23());
	private static final long[] mk_tokenSet_24() {
		long[] data = { -5188146770701451264L, 4294901759L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_24 = new BitSet(mk_tokenSet_24());
	private static final long[] mk_tokenSet_25() {
		long[] data = { -5188146770701451264L, 4026531840L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_25 = new BitSet(mk_tokenSet_25());
	private static final long[] mk_tokenSet_26() {
		long[] data = { -5188146770701451264L, 4026535936L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_26 = new BitSet(mk_tokenSet_26());
	private static final long[] mk_tokenSet_27() {
		long[] data = { -5188146770701451264L, 4026537984L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_27 = new BitSet(mk_tokenSet_27());
	private static final long[] mk_tokenSet_28() {
		long[] data = { -5188146770701451264L, 4026595328L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_28 = new BitSet(mk_tokenSet_28());
	private static final long[] mk_tokenSet_29() {
		long[] data = { -5188146770701451264L, 4039178240L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_29 = new BitSet(mk_tokenSet_29());
	private static final long[] mk_tokenSet_30() {
		long[] data = { -5188146770701451264L, 4290836480L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_30 = new BitSet(mk_tokenSet_30());
	private static final long[] mk_tokenSet_31() {
		long[] data = { 0L, 394560L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_31 = new BitSet(mk_tokenSet_31());
	private static final long[] mk_tokenSet_32() {
		long[] data = { -5188146770701451264L, 4290836489L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_32 = new BitSet(mk_tokenSet_32());
	private static final long[] mk_tokenSet_33() {
		long[] data = { 303992974860091392L, 1984274956332L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_33 = new BitSet(mk_tokenSet_33());
	private static final long[] mk_tokenSet_34() {
		long[] data = { 303992974860091392L, 1975685021704L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_34 = new BitSet(mk_tokenSet_34());
	private static final long[] mk_tokenSet_35() {
		long[] data = { 2305843043539878144L, 1099511627776L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_35 = new BitSet(mk_tokenSet_35());
	
	}
