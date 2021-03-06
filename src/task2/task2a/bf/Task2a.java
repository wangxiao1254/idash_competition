package task2.task2a.bf;

import java.util.Arrays;
import java.util.HashSet;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;

import task2.bloomFilter.BF;
import task2.circuit_from_compiler.BF_circuit;
import task2.task2a.PrepareData;
import task2.task2a.SNPEntry;
import util.EvaRunnable;
import util.GenRunnable;
import util.Utils;
import circuits.arithmetic.IntegerLib;
import flexsc.CompEnv;
import flexsc.Flag;

public class Task2a {
	public static double NoM = 1.5;

	public static<T> T[] compute(CompEnv<T> env, T[] aliceBF, T[] bobBF) {
		IntegerLib<T> lib = new IntegerLib<>(env);
		T[] aUb = lib.or(aliceBF, bobBF);
		return lib.numberOfOnes(aUb);
	}

	public static int nextPower(int a) {
		int i = 1;
		while(i < a) {
			i*=2;
		}
		return i;
	}

	public static int parameterChoose(int totalSize) {
		int l;
		if(totalSize <= 500)
			l = 15000;
		else if (totalSize <= 30000)
			l = 30000;
		else l =  totalSize;
		return l;
	}

	public static<T> T[] computeAuto(CompEnv<T> env, T[] aliceBF, T[] bobBF) throws Exception {
		BF_circuit<T> lib2 = new BF_circuit<T>(env);
		return lib2.merge(aliceBF.length, aliceBF, bobBF);
	}

	static CommandLine processArgs(String[] args) throws Exception {
		Options options = new Options();
		options.addOption("f", "file", true, "file");
		options.addOption("p", "filprecisione", true, "precision");
		options.addOption("a", "automated", false, "automated");

		CommandLineParser parser = new BasicParser();
		CommandLine cmd = parser.parse(options, args);

		if(!cmd.hasOption("f")) {
			throw new Exception("argument format is wrong!");
		}
		return cmd;
	}

	public static class Generator<T> extends GenRunnable<T> {
		T[] aliceBF;
		T[] bobBF;
		T[] res;
		int totalSize;
		boolean automated;
		BF bf;
		@Override
		public void prepareInput(CompEnv<T> gen) throws Exception {
			CommandLine cmd = processArgs(args);
			automated = cmd.hasOption("a");

			HashSet<SNPEntry> data = PrepareData.readFile(cmd.getOptionValue("f"));

			int alicelength = data.size();
			gen.channel.writeInt(data.size());
			gen.channel.flush();
			int boblength = gen.channel.readInt();
			totalSize = boblength+alicelength;

			if(cmd.hasOption("p")) {
				NoM = new Integer(cmd.getOptionValue("p"));
				bf = new BF(boblength+alicelength, (int) (NoM*totalSize));
			} else 
				bf = new BF(boblength+alicelength, parameterChoose(totalSize));

			for(int i = 0; i < bf.k; ++i)
				gen.channel.writeByte(bf.sks[i],bf.sks[i].length);
			gen.channel.flush();

			for(SNPEntry e : data)
				bf.insert(e.toString());
		}

		@Override
		public void secureCompute(CompEnv<T> gen) throws Exception {
			IntegerLib<T> lib = new IntegerLib<>(gen);
			res = lib.zeros(32);
			for(int i = 0; i < bf.bs.length; i+=Flag.OTBlockSize) {
				int len = Math.min(bf.bs.length, i+Flag.OTBlockSize);
				aliceBF = gen.inputOfAlice(Arrays.copyOfRange(bf.bs, i, len));
				bobBF =  gen.inputOfBob(Arrays.copyOfRange(bf.bs, i, len));
				T[] tmp = null;
				if(automated)
					tmp = computeAuto(gen, lib.padSignal(aliceBF, nextPower(aliceBF.length)), lib.padSignal(bobBF, nextPower(bobBF.length)));
				else tmp = compute(gen, aliceBF, bobBF);
				res = lib.add(res, lib.padSignal(tmp, 32));
			}
		}

		@Override
		public void prepareOutput(CompEnv<T> gen) {
			int r = Utils.toInt(gen.outputToAlice(res));
			r = BF.countToSize(r, bf.k, bf.m);
			System.out.println("Hamming Distance: "+(2*r-totalSize));
		}
	}

	public static class Evaluator<T> extends EvaRunnable<T> {
		T[] aliceBF;
		T[] bobBF;
		T[] res;
		BF bf;
		boolean automated;
		@Override
		public void prepareInput(CompEnv<T> gen) throws Exception {
			CommandLine cmd = processArgs(args);
			automated = cmd.hasOption("a");
			if(cmd.hasOption("p"))
				NoM = new Integer(cmd.getOptionValue("p"));
			HashSet<SNPEntry> data = PrepareData.readFile(cmd.getOptionValue("f"));
			int boblength = data.size();

			gen.channel.writeInt(data.size());
			gen.channel.flush();
			int alicelength = gen.channel.readInt();

			int totalSize = alicelength + boblength;
			if(cmd.hasOption("p")) {
				NoM = new Integer(cmd.getOptionValue("p"));
				bf = new BF(boblength+alicelength, (int) (NoM*totalSize));
			}
			else 
				bf = new BF(boblength+alicelength, parameterChoose(totalSize));

			for(int i = 0; i < bf.k; ++i)
				bf.sks[i] = gen.channel.readBytes(bf.sks[i].length);

			for(SNPEntry e : data)
				bf.insert(e.toString());
		}

		@Override
		public void secureCompute(CompEnv<T> gen) throws Exception {
			IntegerLib<T> lib = new IntegerLib<>(gen);
			res = lib.zeros(32);
			for(int i = 0; i < bf.bs.length; i+=Flag.OTBlockSize) {
				int len = Math.min(bf.bs.length, i+Flag.OTBlockSize);
				aliceBF = gen.inputOfAlice(Arrays.copyOfRange(bf.bs, i, len));
				bobBF =  gen.inputOfBob(Arrays.copyOfRange(bf.bs, i, len));
				T[] tmp = null;
				if(automated)
					tmp = computeAuto(gen, lib.padSignal(aliceBF, nextPower(aliceBF.length)), lib.padSignal(bobBF, nextPower(bobBF.length)));
				else tmp = compute(gen, aliceBF, bobBF);
				res = lib.add(res, lib.padSignal(tmp, 32));
			}
		}

		@Override
		public void prepareOutput(CompEnv<T> gen) {
			gen.outputToAlice(res);
		}
	}
}