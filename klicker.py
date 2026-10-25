import pyautogui
import time

print("Automatischer Klicker läuft... (Beenden mit Strg+C im Terminal)")

while True:
    try:
        # Sucht das Bild des Buttons auf dem Bildschirm (80% Ähnlichkeit reicht aus)
        button = pyautogui.locateOnScreen('accept_button.png', confidence=0.8)
        
        if button is not None:
            pyautogui.click(button)
            print("Accept All wurde geklickt!")
            time.sleep(2)  # Wartet kurz nach dem Klick, um Mehrfachklicks zu verhindern
            
    except Exception:
        # Wenn der Button gerade nicht zu sehen ist, macht das Skript einfach weiter
        pass
    
    # 1 Sekunde Pause, damit dein PC nicht überlastet wird
    time.sleep(1)