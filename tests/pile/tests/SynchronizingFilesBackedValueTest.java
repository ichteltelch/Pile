package pile.tests;

import java.awt.GridLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import pile.impl.Piles;
import pile.interop.exec.StandardExecutors;
import pile.interop.preferences.SynchronizingFilesBackedValue;

public class SynchronizingFilesBackedValueTest {
	public static void main(String[] args) throws Exception  {
		Path root = Paths.get("/tmp/piles/sfbvTest");
		//create directories
		Files.createDirectories(root);
		int spokes = 3;
		int length = 5;
		Path[][] spokesFiles = new Path[spokes][length];
		for(int i=0; i<spokes; i++) {
			for(int j=0; j<length; j++) {
				spokesFiles[i][j] = root.resolve(i+"-"+j);
			}
		}

		SynchronizingFilesBackedValue<String> hub;
		@SuppressWarnings("unchecked")
		SynchronizingFilesBackedValue<String> spokesValues[][] 
				= new SynchronizingFilesBackedValue[spokes][length];

		Supplier<String> defaultSupplier = ()->"Test";
		Function<SynchronizingFilesBackedValue<String>, JComponent> createGui = (v)->{
			JTextField text = new JTextField(10);
			text.setFont(text.getFont().deriveFont(40f));
			text.setEditable(true);
			v.addValueListener((e)->SwingUtilities.invokeLater(()->text.setText(v.get())));
			text.addActionListener(e->SwingUtilities.invokeLater(()->v.set(text.getText())));
			text.setText(v.get());
			return text;
		};
		{
			Path[] hubFiles = new Path[spokes];
			for(int i=0; i<spokes; i++) {
				hubFiles[i] = spokesFiles[i][0];
			}
			hub = new SynchronizingFilesBackedValue<String>(
					Piles.constant(Arrays.asList(hubFiles)), 
					SynchronizingFilesBackedValue.STRING_CODEC, 
					defaultSupplier);
			hub.name("Hub");
			hub.dependencyName();
			hub.autoPoll(StandardExecutors.delayed(), 1000);
		}
		for(int i=0; i<spokes; i++) {
			for(int j=0; j<length; j++) {
				Collection<Path> spokeFiles;
				if(j+1<length)
					spokeFiles = Arrays.asList(spokesFiles[i][j], spokesFiles[i][j+1]);
				else {
					spokeFiles = Collections.singleton(spokesFiles[i][j]);
				}
				spokesValues[i][j] = new SynchronizingFilesBackedValue<String>(
						Piles.constant(spokeFiles), 
						SynchronizingFilesBackedValue.STRING_CODEC, 
						defaultSupplier);
				spokesValues[i][j].autoPoll(StandardExecutors.delayed(), 1000 + i*2000);
				spokesValues[i][j].name("Spoke "+i+"-"+j);
			}
		}



		hub.pollOnce();
		for(int i=0; i<spokes; i++) {
			for(int j=0; j<length; j++) {
				spokesValues[i][j].pollOnce();
			}
		}
		SwingUtilities.invokeLater(()->{
			JComponent hubGui;
			JComponent spokesGui[][] = new JComponent[spokes][length];
			hubGui = createGui.apply(hub);
			JPanel gui = new JPanel(new GridLayout(spokes, length+1));
			for(int i=0; i<spokes; i++) {
				if(i==0) {
					gui.add(hubGui);
				}else {
					gui.add(Box.createGlue());
				}
				for(int j=0; j<length; j++) {
					spokesGui[i][j] = createGui.apply(spokesValues[i][j]);
					gui.add(spokesGui[i][j]);
				}

			}
			JFrame f = new JFrame("SynchronizingFilesBackedValue Test");
			f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			f.add(gui);
			f.pack();
			f.setVisible(true);
		});

	}
}
