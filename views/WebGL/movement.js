// on mousclick send front vec
// incrementaly send position updates

const movement = {
    init: function() {
        // Add key listeners
        document.addEventListener('keydown', (event) => this.handleKeyDown(event));

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


        this.sendUpdateKey({
            keys: Array.from(this.activeKeys)
        });
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

    sendUpdatePos: async function(data) {
        console.log(data);
        try {
            await fetch('http://localhost:' + window.location.port + '/position', {
                method: 'POST',
                body: JSON.stringify(data),
                headers: {
                    'Content-Type': 'application/json',
                    'Connection': 'keep-alive'
                },
                keepalive: true
            });
        } catch (error) {
            console.error('Failed to send position update:', error);
        }
    },

    sendUpdateFrontVec: async function(data) {
        console.log(data);
        try {
            await fetch('http://localhost:' + window.location.port + '/frontvector', {
                method: 'POST',
                body: JSON.stringify(data),
                headers: {
                    'Content-Type': 'application/json',
                    'Connection': 'keep-alive'
                },
                keepalive: true
            });
        } catch (error) {
            console.error('Failed to send front facing vector update:', error);
        }
    }
}
movement.init();
