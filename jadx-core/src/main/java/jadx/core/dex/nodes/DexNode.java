package jadx.core.dex.nodes;

import jadx.core.dex.info.ClassInfo;
import jadx.core.dex.info.FieldInfo;
import jadx.core.dex.info.MethodInfo;
import jadx.core.dex.instructions.args.ArgType;
import jadx.core.utils.exceptions.DecodeException;
import jadx.core.utils.files.InputFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.android.dx.io.ClassData;
import com.android.dx.io.ClassData.Method;
import com.android.dx.io.ClassDef;
import com.android.dx.io.Code;
import com.android.dx.io.DexBuffer;
import com.android.dx.io.DexBuffer.Section;
import com.android.dx.io.FieldId;
import com.android.dx.io.MethodId;
import com.android.dx.io.ProtoId;
import com.android.dx.merge.TypeList;

public class DexNode {

	public static final int NO_INDEX = -1;

	private final RootNode root;
	private final DexBuffer dexBuf;
	private final List<ClassNode> classes = new ArrayList<ClassNode>();
	private final String[] strings;

	private final Map<Object, FieldNode> constFields = new HashMap<Object, FieldNode>();

	public DexNode(RootNode root, InputFile input) {
		this.root = root;
		this.dexBuf = input.getDexBuffer();

		List<String> stringList = dexBuf.strings();
		this.strings = stringList.toArray(new String[stringList.size()]);
	}

	public void loadClasses() throws DecodeException {
		for (ClassDef cls : dexBuf.classDefs()) {
			classes.add(new ClassNode(this, cls));
		}
	}

	public List<ClassNode> getClasses() {
		return classes;
	}

	public ClassNode resolveClass(ClassInfo clsInfo) {
		return root.resolveClass(clsInfo);
	}

	public MethodNode resolveMethod(MethodInfo mth) {
		ClassNode cls = resolveClass(mth.getDeclClass());
		if (cls != null) {
			return cls.searchMethod(mth);
		}
		return null;
	}

	public FieldNode resolveField(FieldInfo field) {
		ClassNode cls = resolveClass(field.getDeclClass());
		if (cls != null) {
			return cls.searchField(field);
		}
		return null;
	}

	public Map<Object, FieldNode> getConstFields() {
		return constFields;
	}

	// DexBuffer wrappers

	public String getString(int index) {
		return strings[index];
	}

	public ArgType getType(int index) {
		return ArgType.parse(getString(dexBuf.typeIds().get(index)));
	}

	public MethodId getMethodId(int mthIndex) {
		return dexBuf.methodIds().get(mthIndex);
	}

	public FieldId getFieldId(int fieldIndex) {
		return dexBuf.fieldIds().get(fieldIndex);
	}

	public ProtoId getProtoId(int protoIndex) {
		return dexBuf.protoIds().get(protoIndex);
	}

	public ClassData readClassData(ClassDef cls) {
		return dexBuf.readClassData(cls);
	}

	public List<ArgType> readParamList(int parametersOffset) {
		TypeList paramList = dexBuf.readTypeList(parametersOffset);
		List<ArgType> args = new ArrayList<ArgType>(paramList.getTypes().length);
		for (short t : paramList.getTypes()) {
			args.add(getType(t));
		}
		return Collections.unmodifiableList(args);
	}

	public Code readCode(Method mth) {
		return dexBuf.readCode(mth);
	}

	public Section openSection(int offset) {
		return dexBuf.open(offset);
	}

	public RootNode root() {
		return root;
	}

	@Override
	public String toString() {
		return "DEX";
	}
}
