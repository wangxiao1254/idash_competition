package task2.task2a.automated;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;

import network.Server;
import task2.bloomFilter.BF;
import task2.task2a.PrepareData;
import task2.task2a.SNPEntry;
import util.EvaRunnable;
import util.GenRunnable;
import util.Utils;
import flexsc.CompEnv;

public class Task2 {
	public static final int LEN = 25;
	
	public static<T> T[] compute(CompEnv<T> env, T[][] scData, int length) {
		Task2Automated<T> a;
		T[] ret = null;
		try {
			a = new Task2Automated<T>(env, scData[0].length, (int) Math.ceil(Math.log(length)/Math.log(2)) );
			ret = a.funct(scData, scData.length);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return ret;
	}

	
	public static class Generator<T> extends GenRunnable<T> {
		T[][] scData;
		T[] res;
		BF bf;
		int totalSize = 0;
		
		@Override
		public void prepareInput(CompEnv<T> gen) {
			HashSet<SNPEntry> data = PrepareData.readFile(args[0]);
			int alicelength = data.size();
			
			byte[] boblengthraw = null;
			try {
				gen.os.write(ByteBuffer.allocate(4).putInt(data.size()).array());
				gen.os.flush();
				boblengthraw = Server.readBytes(gen.is, 4);
			} catch (IOException e) {
				e.printStackTrace();
			}
			int boblength = ByteBuffer.wrap(boblengthraw).getInt();
			totalSize = boblength+alicelength;
			
			long[] in = new long[alicelength];
			int cnt = 0;
			for(SNPEntry e : data) {
				in[cnt] = SNPEntry.HashToLong(e.toString(), LEN);
				cnt++;
			}
			Arrays.sort(in);
			
			boolean[][] clear = new boolean[alicelength][];
			for(int i = 0; i < in.length;  ++i)
				clear[i] = Utils.fromLong(in[i], LEN);
			
			T[][] Alice = gen.inputOfAlice(clear);
			T[][] Bob = gen.inputOfBob(new boolean[boblength][LEN]);
			
			scData = gen.newTArray(totalSize, LEN);
			
			System.arraycopy(Alice, 0, scData, 0, Alice.length);
			System.arraycopy(Bob, 0, scData, Alice.length, Bob.length);
		}

		@Override
		public void secureCompute(CompEnv<T> gen) {
			res = compute(gen, scData, totalSize);
		}

		@Override
		public void prepareOutput(CompEnv<T> gen) {
			int r = Utils.toInt(gen.outputToAlice(res));
//			r = bf.countToSize(r);
			System.out.println("res"+(2*r-totalSize));
		}		
	}

	public static class Evaluator<T> extends EvaRunnable<T> {
		T[][] scData;
		T[] res;
		int totalSize;
		
		@Override
		public void prepareInput(CompEnv<T> gen) {
			HashSet<SNPEntry> data = PrepareData.readFile(args[0]);
			int boblength = data.size();
			byte[] alicelengthraw = null;
			try {
				gen.os.write(ByteBuffer.allocate(4).putInt(data.size()).array());
				gen.os.flush();
				alicelengthraw = Server.readBytes(gen.is, 4);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			int alicelength = ByteBuffer.wrap(alicelengthraw).getInt();
			long[] in = new long[boblength];
			int cnt = 0;
			for(SNPEntry e : data) {
				in[cnt] = -1*SNPEntry.HashToLong(e.toString(), LEN);
				cnt++;
			}
			totalSize = alicelength+boblength;
			Arrays.sort(in);
			
			boolean[][] clear = new boolean[boblength][];
			for(int i = 0; i < in.length;  ++i)
				clear[i] = Utils.fromLong(-1*in[i], LEN);

			T[][] Alice = gen.inputOfAlice(new boolean[alicelength][LEN]);
			T[][] Bob = gen.inputOfBob(clear);
			scData = gen.newTArray(Alice.length+Bob.length, LEN);
			
			System.arraycopy(Alice, 0, scData, 0, Alice.length);
			System.arraycopy(Bob, 0, scData, Alice.length, Bob.length);
		}

		@Override
		public void secureCompute(CompEnv<T> gen) {
			res = compute(gen, scData, totalSize);
		}

		@Override
		public void prepareOutput(CompEnv<T> gen) {
			gen.outputToAlice(res);
		}
	}
}