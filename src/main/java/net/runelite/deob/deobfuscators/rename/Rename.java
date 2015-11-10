package net.runelite.deob.deobfuscators.rename;

import edu.ucla.sspace.graph.Graph;
import edu.ucla.sspace.graph.isomorphism.IsomorphismTester;
import edu.ucla.sspace.graph.isomorphism.TypedVF2IsomorphismTester;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.deob.ClassGroup;
import net.runelite.deob.Method;
import net.runelite.deob.attributes.code.Instruction;
import net.runelite.deob.attributes.code.instruction.types.InvokeInstruction;
import net.runelite.deob.execution.Execution;
import net.runelite.deob.execution.Frame;

public class Rename
{
	// respective executions
	private Execution eone, etwo;
	
	// old -> new object mapping
	private Map<Object, Object> objMap = new HashMap<>();
	
	// methods which have been processed in the original
	private Set<Method> processed = new HashSet<>();
	
	private boolean compare(Frame f1, Frame f2)
	{
		Graph g1 = f1.getMethodCtx().getGraph(), g2 = f2.getMethodCtx().getGraph();
		
		IsomorphismTester isoTest = new TypedVF2IsomorphismTester();
		if (!isoTest.areIsomorphic(g1, g2))
			return false;
		
		Map<Integer, Integer> mapping = isoTest.findIsomorphism(g1, g2);
		Map<Integer, Instruction> map1 = f1.getMethodCtx().getIdMap(), map2 = f2.getMethodCtx().getIdMap();
		
		for (Entry<Integer, Integer> e : mapping.entrySet())
		{
//			if (e.getKey() == null || e.getValue() == null)
//			{
//				assert e.getKey() == e.getValue();
//				continue;
//			}
			
			Instruction i1 = map1.get(e.getKey());
			Instruction i2 = map2.get(e.getValue());
			
			assert i1.getClass() == i2.getClass();
			
			InvokeInstruction ii1 = (InvokeInstruction) i1, ii2 = (InvokeInstruction) i2;
			
			assert ii1.getMethods().size() == ii2.getMethods().size();
			
			for (int i = 0; i < ii1.getMethods().size(); ++i)
			{
				Method m1 = ii1.getMethods().get(i), m2 = ii2.getMethods().get(i);
				
//				assert objMap.containsKey(m1) == false || objMap.get(m1) == m2;
				objMap.put(m1, m2);
			}
			
			System.out.println("MATCH " + ii1.getMethods().get(0).getName() + " -> " + ii2.getMethods().get(0).getName());
		}
		
		return true;
	}
	
	private void process(Method one, Method two)
	{
		// get frames for respective methods
		List<Frame> f1 = eone.processedFrames.stream().filter(f -> f.getMethod() == one).collect(Collectors.toList());
		List<Frame> f2 = etwo.processedFrames.stream().filter(f -> f.getMethod() == two).collect(Collectors.toList());
		
		Frame p1 = null, p2 = null;
		outer:
		for (Frame fr1 : f1)
			for (Frame fr2 : f2)
			{
				if (p1 == null) p1 = fr1;
				if (p2 == null) p2 = fr2;
				
				assert fr1.getMethodCtx() == p1.getMethodCtx();
				assert fr2.getMethodCtx() == p2.getMethodCtx();
			}
		
		for (Frame fr1 : f1)
			for (Frame fr2 : f2)
			{
				compare(fr1, fr2);
				break;
			}
		
		System.out.println("end");
	}
	public void run(ClassGroup one, ClassGroup two)
	{
		eone = new Execution(one);
		eone.populateInitialMethods();
		List<Method> initial1 = eone.getInitialMethods().stream().sorted((m1, m2) -> m1.getName().compareTo(m2.getName())).collect(Collectors.toList());
		eone.run();
		
		etwo = new Execution(two);
		etwo.populateInitialMethods();
		List<Method> initial2 = etwo.getInitialMethods().stream().sorted((m1, m2) -> m1.getName().compareTo(m2.getName())).collect(Collectors.toList());
		etwo.run();
		
		assert initial1.size() == initial2.size();
		
		for (int i = 0; i < initial1.size(); ++i)
		{
			Method m1 = initial1.get(i), m2 = initial2.get(i);
			
			assert m1.getName().equals(m2.getName());
			
			objMap.put(m1, m2);
		}

//		process(
//			initial1.get(0).getMethod(),
//			initial2.get(0).getMethod()
//		);
//		processed.add(initial1.get(0).getMethod());
//		process(
//			one.findClass("class143").findMethod("run"),
//			two.findClass("class143").findMethod("run")
//		);
//		processed.add(one.findClass("client").findMethod("init"));
		
		for (;;)
		{
			Optional next = objMap.keySet().stream()
				.filter(m -> !processed.contains(m))
				.findAny();
			if (!next.isPresent())
				break;
			
			Method m = (Method) next.get();
			Method m2 = (Method) objMap.get(m);
			
			System.out.println("Scanning " + m.getName() + " -> " + m2.getName());
			process(m, m2);
			processed.add(m);
		}
		
		for (Entry<Object, Object> e : objMap.entrySet())
		{
			Method m1 = (Method) e.getKey();
			Method m2 = (Method) e.getValue();
			
			System.out.println("FINAL " + m1.getMethods().getClassFile().getName() + "." + m1.getName() + " -> " + m2.getMethods().getClassFile().getName() + "." + m2.getName());
		}

		System.out.println("done count " + objMap.size());
	}
}
