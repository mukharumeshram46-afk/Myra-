/**
 * Namu Capacitor & Native Bridge Client
 * Wraps @capacitor/core or window.NamuBridge to invoke native Android OS controls.
 */

declare global {
  interface Window {
    NamuBridge?: {
      click: (x: number, y: number) => string;
      findAndClick: (text: string) => string;
      openApp: (packageName: string) => string;
      scroll: (direction: string) => string;
      inspectTree: () => string;
      typeText: (text: string) => string;
      readNotifications: () => string;
      startFloatingHUD: () => string;
      stopFloatingHUD: () => string;
      setNamuEmotion: (emotion: string) => string;
      speakAndExecute: (spokenText: string, actionJson: string) => string;
    };
    Capacitor?: any;
  }
}

export interface NodeElement {
  className?: string;
  text?: string;
  desc?: string;
  id?: string;
  clickable?: boolean;
  editable?: boolean;
  bounds?: [number, number, number, number];
}

export const NamuBridgeService = {
  click(x: number, y: number): Promise<boolean> {
    if (window.NamuBridge?.click) {
      try {
        const res = JSON.parse(window.NamuBridge.click(x, y));
        return Promise.resolve(res.status === 'success');
      } catch (e) {
        return Promise.resolve(false);
      }
    }
    console.log(`[Mock Bridge] click(${x}, ${y})`);
    return Promise.resolve(true);
  },

  findAndClick(text: string): Promise<boolean> {
    if (window.NamuBridge?.findAndClick) {
      try {
        const res = JSON.parse(window.NamuBridge.findAndClick(text));
        return Promise.resolve(res.status === 'success');
      } catch (e) {
        return Promise.resolve(false);
      }
    }
    console.log(`[Mock Bridge] findAndClick('${text}')`);
    return Promise.resolve(true);
  },

  openApp(packageName: string): Promise<boolean> {
    if (window.NamuBridge?.openApp) {
      try {
        const res = JSON.parse(window.NamuBridge.openApp(packageName));
        return Promise.resolve(res.status === 'success');
      } catch (e) {
        return Promise.resolve(false);
      }
    }
    console.log(`[Mock Bridge] openApp('${packageName}')`);
    return Promise.resolve(true);
  },

  scroll(direction: 'up' | 'down' | 'left' | 'right' = 'down'): Promise<boolean> {
    if (window.NamuBridge?.scroll) {
      try {
        const res = JSON.parse(window.NamuBridge.scroll(direction));
        return Promise.resolve(res.status === 'success');
      } catch (e) {
        return Promise.resolve(false);
      }
    }
    console.log(`[Mock Bridge] scroll('${direction}')`);
    return Promise.resolve(true);
  },

  inspectTree(): Promise<any> {
    if (window.NamuBridge?.inspectTree) {
      try {
        return Promise.resolve(JSON.parse(window.NamuBridge.inspectTree()));
      } catch (e) {
        return Promise.resolve({ error: 'Failed parsing tree' });
      }
    }
    return Promise.resolve({
      foregroundApp: 'com.whatsapp',
      nodeCount: 14,
      elements: [
        { text: 'Rahul Sharma', clickable: true, bounds: [48, 200, 1000, 280] },
        { text: 'Hey, are you free for the sync?', clickable: false },
        { desc: 'Type a message', id: 'com.whatsapp:id/entry', clickable: true, editable: true },
        { desc: 'Send', id: 'com.whatsapp:id/send', clickable: true }
      ]
    });
  },

  setEmotion(emotion: string): void {
    if (window.NamuBridge?.setNamuEmotion) {
      window.NamuBridge.setNamuEmotion(emotion);
    }
  },

  startFloatingHUD(): void {
    if (window.NamuBridge?.startFloatingHUD) {
      window.NamuBridge.startFloatingHUD();
    }
  }
};
