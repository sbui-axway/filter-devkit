package com.vordel.circuit.script.context.resources;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;

import javax.el.ELException;

import org.w3c.dom.Document;

import com.fasterxml.jackson.databind.JsonNode;
import com.vordel.circuit.script.ScriptHelper;
import com.vordel.common.Dictionary;
import com.vordel.el.DictionaryResolver;
import com.vordel.el.Selector;
import com.vordel.el.SelectorCoercion;
import com.vordel.mime.Body;
import com.vordel.mime.JSONBody;
import com.vordel.mime.XMLBody;
import com.vordel.trace.Trace;

public class SelectorResource<T> implements SubstitutableResource<T> {
	private Selector<T> selector;

	public SelectorResource(String expression, Class<T> clazz) {
		this(fromLiteral(expression, clazz, false));
	}

	public SelectorResource(Selector<T> selector) {
		this.selector = selector;
	}

	@Override
	public T substitute(Dictionary dict) {
		return selector == null ? null : selector.substitute(dict);
	}

	public final static <T> Selector<T> fromLiteral(String literal, Class<T> clazz, boolean trim) {
		if ((literal != null) && (trim)) {
			literal = literal.trim();

			if (literal.isEmpty()) {
				literal = null;
			}
		}

		return literal == null ? null : new Selector<T>(literal, clazz);
	}

	/**
	 * create a selector from the given expression (without juel start/end
	 * delimiters). purpose of this method is to create usable selectors from
	 * scripts. It's equivalent of SelectorExpression parameter injection.
	 * 
	 * @param <T>        expected type
	 * @param expression name of attribute in message (can be a selector attribute)
	 * @param clazz      expected type
	 * @return selector for the given attribute.
	 */
	public static <T> Selector<T> fromExpression(String expression, Class<T> clazz) {
		return SelectorResource.fromLiteral(String.format("${commons:value(%s)}", expression), clazz, true);
	}

	/**
	 * create a selector from a literal. This method is miss named, use
	 * {@link #fromLiteral(String, Class, boolean)} instead
	 * 
	 * @param <T>        expected type
	 * @param expression selector literal
	 * @param clazz      class for selector coercion
	 * @param trim       if 'true' trim literal before creating selector
	 * @return a new selector
	 */
	@Deprecated
	public final static <T> Selector<T> createSelector(String expression, Class<T> clazz, boolean trim) {
		return fromLiteral(expression, clazz, trim);
	}

	static {
		/* each time this class is loaded, register Selector functions */
		registerFunction("commons", "value", "value", Object.class);
		registerFunction("commons", "isNull", "isNull", Object.class);

		/* register additional register conversions */

		/* Body -> InputStream */
		Selector.registerConversion(InputStream.class, new SelectorCoercion<InputStream>() {
			@Override
			public boolean canConvertFrom(Class<?> c) {
				return Body.class.isAssignableFrom(c);
			}

			@Override
			public InputStream convert(Object o, Class<InputStream> type) {
				return ScriptHelper.toInputStream((Body) o);
			}
		});

		/* Body -> byte[] */
		Selector.registerConversion(byte[].class, new SelectorCoercion<byte[]>() {
			@Override
			public boolean canConvertFrom(Class<?> c) {
				return Body.class.isAssignableFrom(c);
			}

			@Override
			public byte[] convert(Object o, Class<byte[]> type) {
				return ScriptHelper.toByteArray((Body) o);
			}
		});

		/* Body -> JsonNode */
		Selector.registerConversion(JsonNode.class, new SelectorCoercion<JsonNode>() {
			@Override
			public boolean canConvertFrom(Class<?> c) {
				return JSONBody.class.isAssignableFrom(c);
			}

			@Override
			public JsonNode convert(Object o, Class<JsonNode> type) {
				try {
					return ((JSONBody) o).getJSON();
				} catch (IOException e) {
					throw new ELException("can't convert body to json", e);
				}
			}
		});

		/* Body -> Document */
		Selector.registerConversion(Document.class, new SelectorCoercion<Document>() {
			@Override
			public boolean canConvertFrom(Class<?> c) {
				return XMLBody.class.isAssignableFrom(c);
			}

			@Override
			public Document convert(Object o, Class<Document> type) {
				try {
					return ((XMLBody) o).getDocument();
				} catch (IOException e) {
					throw new ELException("can't convert body to json", e);
				}
			}
		});

		/* JsonNode|InputStream|String -> JSONBody */
		Selector.registerConversion(JSONBody.class, new SelectorCoercion<JSONBody>() {
			@Override
			public boolean canConvertFrom(Class<?> c) {
				return JsonNode.class.isAssignableFrom(c) || InputStream.class.isAssignableFrom(c) || String.class.isAssignableFrom(c);
			}

			@Override
			public JSONBody convert(Object o, Class<JSONBody> type) {
				if (o instanceof JsonNode) {
					return ScriptHelper.toJSONBody((JsonNode) o);
				} else if (o instanceof InputStream) {
					return ScriptHelper.toJSONBody((InputStream) o);
				} else if (o instanceof String) {
					return ScriptHelper.toJSONBody((String) o);
				}

				throw new ELException("illegal conversion input");
			}
		});

		/* Document|InputStream|String -> XMLBody */
		Selector.registerConversion(XMLBody.class, new SelectorCoercion<XMLBody>() {
			@Override
			public boolean canConvertFrom(Class<?> c) {
				return Document.class.isAssignableFrom(c) || InputStream.class.isAssignableFrom(c) || String.class.isAssignableFrom(c);
			}

			@Override
			public XMLBody convert(Object o, Class<XMLBody> type) {
				if (o instanceof Document) {
					return ScriptHelper.toXMLBody((Document) o);
				} else if (o instanceof InputStream) {
					return ScriptHelper.toXMLBody((InputStream) o);
				} else if (o instanceof String) {
					return ScriptHelper.toXMLBody((String) o);
				}

				throw new ELException("illegal conversion input");
			}
		});
	}

	private static void registerFunction(String prefix, String name, String methodName, Class<?>... args) {
		try {
			Method method = SelectorResource.class.getDeclaredMethod(methodName, args);

			Selector.registerFunction(prefix, name, method);
		} catch (NoSuchMethodException e) {
			Trace.error("unexpected exception", e);
		}
	}

	public static final Object value(Object value) {
		/*
		 * this method is exported as selector function. It is not available in policy
		 * studio (will generate an exception when loading policies). This function is
		 * available once a script has bound its methods
		 */
		return isNull(value) ? null : value;
	}

	public static final boolean isNull(Object value) {
		/*
		 * this method is exported as selector function. It is not available in policy
		 * studio (will generate an exception when loading policies). This function is
		 * available once a script has bound its methods
		 */
		return (value == null) || DictionaryResolver.isResolvable(value.getClass());
	}
}
