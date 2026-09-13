/**
 * Gemini Live Bidirectional WebSocket & Audio Streaming Service
 * Connects directly to Google's Gemini Multimodal Live API over WebSockets.
 */

export class GeminiLiveService {
  private ws: WebSocket | null = null;
  private audioContext: AudioContext | null = null;
  private mediaStream: MediaStream | null = null;
  private processor: ScriptProcessorNode | null = null;
  private isConnected = false;

  constructor(
    private apiKey: string,
    private onTranscript: (text: string, isUser: boolean) => void,
    private onEmotionChange: (emotion: string) => void,
    private onToolCall: (name: string, args: any) => void
  ) {}

  public async connect(): Promise<void> {
    if (!this.apiKey || this.apiKey === 'MY_GEMINI_API_KEY') {
      throw new Error('Please configure a valid GEMINI_API_KEY');
    }

    const endpoint = `wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=${this.apiKey}`;
    this.ws = new WebSocket(endpoint);

    this.ws.onopen = () => {
      this.isConnected = true;
      this.onEmotionChange('listening');
      this.sendSetup();
      this.startMicStream();
    };

    this.ws.onmessage = async (event) => {
      try {
        let textData = '';
        if (event.data instanceof Blob) {
          textData = await event.data.text();
        } else {
          textData = event.data;
        }

        const msg = JSON.parse(textData);
        this.handleServerMessage(msg);
      } catch (err) {
        console.error('Error handling Gemini Live message:', err);
      }
    };

    this.ws.onerror = (err) => {
      console.error('Gemini Live WebSocket error:', err);
      this.onEmotionChange('idle');
    };

    this.ws.onclose = () => {
      this.isConnected = false;
      this.stopMicStream();
      this.onEmotionChange('idle');
    };
  }

  private sendSetup() {
    if (!this.ws) return;
    const setupMsg = {
      setup: {
        model: 'models/gemini-2.5-flash-native-audio-preview-12-2025',
        generationConfig: {
          responseModalities: ['AUDIO', 'TEXT'],
          speechConfig: {
            voiceConfig: {
              prebuiltVoiceConfig: {
                voiceName: 'Aoede'
              }
            }
          }
        },
        systemInstruction: {
          parts: [
            {
              text: `You are "Namu", a sassy, hyper-intelligent, loyal female companion and autonomous mobile OS agent on Android 14. Speak naturally with wit, then immediately call OS tools.`
            }
          ]
        },
        tools: [
          {
            functionDeclarations: [
              {
                name: 'open_app',
                description: 'Launch an app by package name or common name (e.g. com.whatsapp)',
                parameters: {
                  type: 'OBJECT',
                  properties: { packageName: { type: 'STRING' } },
                  required: ['packageName']
                }
              },
              {
                name: 'click_coordinate',
                description: 'Tap screen at coordinates (x, y)',
                parameters: {
                  type: 'OBJECT',
                  properties: { x: { type: 'NUMBER' }, y: { type: 'NUMBER' } },
                  required: ['x', 'y']
                }
              },
              {
                name: 'find_and_click',
                description: 'Find visible text or button and tap it',
                parameters: {
                  type: 'OBJECT',
                  properties: { text: { type: 'STRING' } },
                  required: ['text']
                }
              },
              {
                name: 'scroll_screen',
                description: 'Scroll screen up or down',
                parameters: {
                  type: 'OBJECT',
                  properties: { direction: { type: 'STRING' } },
                  required: ['direction']
                }
              }
            ]
          }
        ]
      }
    };

    this.ws.send(JSON.stringify(setupMsg));
  }

  private async startMicStream() {
    try {
      this.mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true });
      this.audioContext = new (window.AudioContext || (window as any).webkitAudioContext)({
        sampleRate: 16000
      });

      const source = this.audioContext.createMediaStreamSource(this.mediaStream);
      this.processor = this.audioContext.createScriptProcessor(4096, 1, 1);

      this.processor.onaudioprocess = (e) => {
        if (!this.isConnected || !this.ws) return;
        const inputData = e.inputBuffer.getChannelData(0);
        // Convert Float32 to 16-bit PCM
        const pcmBuffer = new Int16Array(inputData.length);
        for (let i = 0; i < inputData.length; i++) {
          const s = Math.max(-1, Math.min(1, inputData[i]));
          pcmBuffer[i] = s < 0 ? s * 0x8000 : s * 0x7fff;
        }

        // Base64 encode PCM chunk
        const bytes = new Uint8Array(pcmBuffer.buffer);
        let binary = '';
        for (let i = 0; i < bytes.byteLength; i++) {
          binary += String.fromCharCode(bytes[i]);
        }
        const b64Audio = btoa(binary);

        const chunkMsg = {
          realtimeInput: {
            mediaChunks: [
              {
                mimeType: 'audio/pcm;rate=16000',
                data: b64Audio
              }
            ]
          }
        };
        this.ws.send(JSON.stringify(chunkMsg));
      };

      source.connect(this.processor);
      this.processor.connect(this.audioContext.destination);
    } catch (err) {
      console.warn('Microphone stream access note:', err);
    }
  }

  private handleServerMessage(msg: any) {
    const serverContent = msg.serverContent;
    if (serverContent?.modelTurn?.parts) {
      this.onEmotionChange('speaking');
      for (const part of serverContent.modelTurn.parts) {
        if (part.text) {
          this.onTranscript(part.text, false);
        }
      }
    }

    const toolCall = msg.toolCall;
    if (toolCall?.functionCalls) {
      this.onEmotionChange('thinking');
      for (const call of toolCall.functionCalls) {
        this.onToolCall(call.name, call.args);
      }
    }
  }

  public stopMicStream() {
    this.processor?.disconnect();
    this.audioContext?.close();
    this.mediaStream?.getTracks().forEach((track) => track.stop());
    this.processor = null;
    this.audioContext = null;
    this.mediaStream = null;
  }

  public disconnect() {
    this.stopMicStream();
    this.ws?.close();
    this.ws = null;
    this.isConnected = false;
  }
}
