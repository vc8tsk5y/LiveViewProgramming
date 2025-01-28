// mnKEvent
const mnKEvent = {
    // set of currently pressed keys
    activeKeys: new Set(),
    // Add properties for batching mouse movements
    mouseDeltaX: 0,
    mouseDeltaY: 0,
    mouseTimeoutId: null,

    init: function() {
        // Add key listeners
        document.addEventListener('keydown', (event) => this.handleKeyDown(event));
        document.addEventListener('keyup', (event) => this.handleKeyUp(event));

        // Add mouse listener
        document.addEventListener('mousedown', (event) => this.handleMouseDown(event));
        document.addEventListener('mousemove', (event) => this.handleMouseMove(event));

        // Lock pointer for FPS-style camera control
        document.addEventListener('click', () => {
            if (!document.pointerLockElement) {
                document.body.requestPointerLock();
            }
        });

        // Handle pointer lock errors
        document.addEventListener('pointerlockerror', () => {
            console.error('Pointer lock failed');
        });
    },

    handleKeyDown: function(event) {
        const key = event.key.toUpperCase();

        // Prevent default behavior for game controls ('R' because i use colemak layout BTW)
        if (['W', 'A', 'S', 'D', ' ', 'R', 'C', '1', '2', '3',].includes(key)) {
            event.preventDefault();
        }

        if (key === 'W' || key === 'A' || key === 'S' || key === 'D' || key === ' ' || key === 'R' || key === 'C' || key === '1' || key === '2' || key === '3') {
            if (!this.activeKeys.has(key)) {
                this.sendUpdateKey({
                    keyDown: key
                });
                this.activeKeys.add(key);
            }
        }
    },

    handleKeyUp: function(event) {
        const key = event.key.toUpperCase();

        if (key === 'W' || key === 'A' || key === 'S' || key === 'D' || key === ' ' || key === 'R' || key === 'C' || key === '1' || key === '2' || key === '3') {
                this.sendUpdateKey({
                    keyUp: key
                });
                this.activeKeys.delete(key);
        }
    },

    handleMouseDown: function(event) {
        if (document.pointerLockElement) {
            this.sendUpdateKey({
                mouseDown: event.button
            });
        }
    },

    handleMouseMove: function(event) {
        if (document.pointerLockElement) {
            // Accumulate mouse deltas
            this.mouseDeltaX += event.movementX;
            this.mouseDeltaY += event.movementY;

            // Schedule send if not already pending
            if (!this.mouseTimeoutId) {
                this.mouseTimeoutId = setTimeout(() => {
                    this.sendUpdateMouse({
                        mouseMoveX: this.mouseDeltaX,
                        mouseMoveY: this.mouseDeltaY
                    });
                    // Reset accumulators and timeout ID
                    this.mouseDeltaX = 0;
                    this.mouseDeltaY = 0;
                    this.mouseTimeoutId = null;
                }, 64); // delay in ms
            }
        }
    },

    sendUpdateKey: async function(data) {
        try {
            await fetch('http://localhost:' + window.location.port + '/keyevent', {
                method: 'POST',
                body: JSON.stringify(data),
                headers: {
                    'Content-Type': 'application/json'
                },
                keepalive: true
            });
        } catch (error) {
            console.error('Failed to send key update:', error);
        }
    },

    // split mouse data to avoid sending to much data to the same url
    sendUpdateMouse: async function(data) {
        try {
            await fetch('http://localhost:' + window.location.port + '/mouseevent', {
                method: 'POST',
                body: JSON.stringify(data),
                headers: {
                    'Content-Type': 'application/json'
                },
                keepalive: true
            });
        } catch (error) {
            console.error('Failed to send mouse update:', error);
        }
    }
}
mnKEvent.init();
// mnKEvent
