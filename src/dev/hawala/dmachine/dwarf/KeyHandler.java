/*
Copyright (c) 2017, Dr. Hans-Walter Latz
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * The name of the author may not be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDER "AS IS" AND ANY EXPRESS
OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package dev.hawala.dmachine.dwarf;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.hawala.dmachine.engine.Config;
import dev.hawala.dmachine.engine.Processes;

/**
 * Key stroke listener for the Dwarf screen pane, forwarding the key-pressed and
 * key-released event to the keyboard-mapper.
 * <p>
 * Handling diacritical start characters (also called dead keys, for example the
 * accent-grave (forward-tick), accent-aigue (back-tick), accent-circonflex (^) etc.)
 * is a problem under Linux, as modern Linuces think to be smart in prehandling those
 * characters to combine them with the next pressed key: this has the net effect
 * that for a press-release cycle, only the release event arrives in the Java window
 * key listener (using the focus management listening alternative does not help,
 * as even being a level deeper in the event pipeline did not help).
 * <br>
 * This is handled here in a questionable, but seemingly working way: if a key-release
 * event arrives for which the key-pressed is missing, first a synthetic key-pressed
 * event is sent to the keyboard-mapper, and the key-release event is delayed by 50 msecs. 
 * </p>
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2017)
 */
public class KeyHandler implements KeyListener {
	private final MainUI mainWindow;

	// executor service for delaying diacritical key events
	private static final ExecutorService executor = Executors.newCachedThreadPool();
	
	// the current pressed keys, for finding missing key-pressed events
	private final List<Integer> currPressed = new ArrayList<>();
	
	// the target for keyboard events
	private final KeyboardMapper keyMapper;
	
	// Are we running on a Mac?
	private final boolean platformMac;


	/**
	 * Constructor.
	 * 
	 * @param keyMapper the target for keyboard events
	 */
	public KeyHandler(MainUI window, KeyboardMapper keyMapper) {
		this.mainWindow = window;
		this.keyMapper = keyMapper;

	  this.platformMac = System.getProperty("os.name").toLowerCase().contains("mac");
	}
	
	// wait 50 ms
	private static void safeWait() {
		try {
			Thread.sleep(50);
		} catch (InterruptedException e) {
		}
	}
	
	private boolean dumpOnVkLess = false;
	
	private int fixExtendedCode(KeyEvent evt) {
		int code = evt.getKeyCode();
		int extendedCode = evt.getExtendedKeyCode();
		// On macOS (as of Java 22, macOS 14.6) the extendedCode given for certain keys
		// is not what one would expect. Unfortunately for these keys it wasn't possible
		// to deal with the issues in the keyboard.map file so we handle them here.
		if (!this.platformMac) return extendedCode;

		if (code == KeyEvent.VK_SLASH) return KeyEvent.VK_SLASH;
		if (code == KeyEvent.VK_BACK_QUOTE) return KeyEvent.VK_BACK_QUOTE;
		if (code == KeyEvent.VK_BACK_SLASH) return KeyEvent.VK_BACK_SLASH;
		if (code == KeyEvent.VK_5) return KeyEvent.VK_5;
		if (evt.isControlDown() && extendedCode == 0 && code != 0) extendedCode = code;
		return extendedCode;
	}

	@Override
	public void keyPressed(KeyEvent evt) {
		int extendedKeyCode = fixExtendedCode(evt);
		
		if (Config.USE_DEBUG_INTERPRETER && extendedKeyCode == KeyEvent.VK_LESS) {
//			System.out.println("key VK_LESS...");
//			System.out.flush();
			
			if (this.dumpOnVkLess) {
				Processes.requestFlightRecorderStopAndDump();
				this.dumpOnVkLess = false;
			} else {
				Processes.requestFlightRecorderStart();
				this.dumpOnVkLess = true;
			}
			
			return;
		}
		
		if (extendedKeyCode == KeyEvent.VK_F12) {
			mainWindow.toggleControls();
		}
		

//		System.out.printf("at %d : panel.keyPressed -> keyCode = %03d, extKeyCode = %05d\n",
//		System.currentTimeMillis(), evt.getKeyCode(), extendedKeyCode);
		this.keyMapper.pressed(extendedKeyCode);
		
		Integer code = Integer.valueOf(extendedKeyCode);
		if (!this.currPressed.contains(code)) { this.currPressed.add(code); }
	}
	
	@Override
	public void keyReleased(KeyEvent evt) {
		int extendedKeyCode = fixExtendedCode(evt);
//		System.out.printf("at %d : panel.keyReleased -> keyCode = %03d, extKeyCode = %05d\n",
//				System.currentTimeMillis(), evt.getKeyCode(), extendedKeyCode);
		
		this.keyMapper.released(extendedKeyCode);
		
		Integer code = Integer.valueOf(extendedKeyCode);
		if (this.currPressed.contains(code)) {
			this.currPressed.remove(code);
		} else {
			System.out.printf("dead key char: 0x%04X\n", code.intValue());
			this.keyMapper.pressed(code.intValue());
			executor.execute(() -> { safeWait(); this.keyMapper.released(code.intValue()); }); 
		}
	}
	
	@Override
	public void keyTyped(KeyEvent evt) {
//		System.out.printf("panel.keyTyped -> keyCode = %03d, extKeyCode = %05d, keyLocation = %d, keyChar = %c\n",
//				evt.getKeyCode(), evt.getExtendedKeyCode(), evt.getKeyLocation(), evt.getKeyChar());
		char c = evt.getKeyChar();
		if (c == 0xFFFD) { // if (c == '�') {
			this.keyMapper.pressed(0x000081);
		} else if (c == '`') {
			this.keyMapper.pressed(0x000080);
		}
	}
		
}
