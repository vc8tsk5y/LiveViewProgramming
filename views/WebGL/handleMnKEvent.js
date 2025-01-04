const mnKEvent = {
    init: function() {
        // Add key listeners
        document.addEventListener('keydown', (event) => this.handleKey(event));

        // Add mouse movement listener
        this.addMouseMoveListener();

        // Lock pointer for FPS-style camera control
        document.addEventListener('click', () => {
            document.body.requestPointerLock();
        });
    },

    addMouseMoveListener: function() {
        // Define the handler and store the reference
        this.mouseMoveHandler = (event) => this.handleMouseMove(event);
        document.addEventListener('mousemove', this.mouseMoveHandler);
    },

    removeMouseMoveListener: function() {
        if (this.mouseMoveHandler) {
            document.removeEventListener('mousemove', this.mouseMoveHandler);
            this.mouseMoveHandler = null; // Clean up the reference
        }
    },

    restartMouseMoveListener: function() {
        // Remove and re-add the mousemove listener
        this.removeMouseMoveListener();
        this.addMouseMoveListener();
    },

    handleKey: function(event) {
        // Prevent default behavior for game controls
        if (['W', 'A', 'S', 'D', ' '].includes(event.key.toUpperCase())) {
            event.preventDefault();
        }

        const keyData = {
            key: event.key,
            timestamp: Date.now() // NOTE: useless?
        };

        this.sendUpdate(keyData);
    },

    handleMouseMove: function(event) {
        if (document.pointerLockElement) {
            const mouseData = {
                mouseMoveX: event.movementX,
                mouseMoveY: event.movementY,
                timestamp: Date.now() // NOTE: useless?
            };
            this.sendUpdate(mouseData);
        }
    },

    sendUpdate: async function(data) {
        const response = await fetch('http://localhost:' + window.location.port + '/mnkevent', {
            method: 'POST',
            body: JSON.stringify(data),
            headers: {
                'Content-Type': 'application/json'
            }
        });
    }
}
// mnKEvent.init();
