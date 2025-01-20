// net::ERR_INVALID_CHUNKED_ENCODING 200 (OK) is the error from /events
// net::ERR_INSUFFICIENT_RESOURCES is the error from /mouseevents
const mnKEvent = {
    // set of currently pressed keys
    activeKeys: new Set(),

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

        // Prevent default behavior for game controls ('R' because i use colemak layout)
        if (['W', 'A', 'S', 'D', ' ', 'R', 'C'].includes(key)) {
            event.preventDefault();
        }

        this.activeKeys.add(key);

        this.sendUpdateKey({
            keys: Array.from(this.activeKeys)
        });
    },

    handleKeyUp: function(event) {
        const key = event.key.toUpperCase();

        this.activeKeys.delete(key);
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
            const mouseData = {
                mouseMoveX: event.movementX,
                mouseMoveY: event.movementY,
            };
            this.sendUpdateMouse(mouseData);
        }
    },

    sendUpdateKey: async function(data) {
        try {
            await fetch('http://localhost:' + window.location.port + '/keyevent', {
                method: 'POST',
                body: JSON.stringify(data),
                headers: {
                    'Content-Type': 'application/json',
                    'Connection': 'keep-alive'
                },
                keepalive: true
            });
        } catch (error) {
            console.error('Failed to send key update:', error);
        }
    },

    sendUpdateMouse: async function(data) {
        try {
            await fetch('http://localhost:' + window.location.port + '/mouseevent', {
                method: 'POST',
                body: JSON.stringify(data),
                headers: {
                    'Content-Type': 'application/json',
                    'Connection': 'keep-alive'
                },
                keepalive: true
            });
        } catch (error) {
            console.error('Failed to send mouse update:', error);
        }
    }
}
mnKEvent.init();
