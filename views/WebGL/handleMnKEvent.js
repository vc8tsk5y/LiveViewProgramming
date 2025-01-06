const mnKEvent = {
    // set of currently pressed keys
    activeKeys: new Set(),

    init: function() {
        // Add key listeners
        document.addEventListener('keydown', (event) => this.handleKeyDown(event));
        document.addEventListener('keyup', (event) => this.handleKeyUp(event));

        // Add mouse movement listener
        document.addEventListener('mousemove', (event) => this.handleMouseMove(event));

        // Lock pointer for FPS-style camera control
        document.addEventListener('click', () => document.body.requestPointerLock());
    },

    handleKeyDown: function(event) {
        // Prevent default behavior for game controls ('R' because i use colemak layout)
        if (['W', 'A', 'S', 'D', ' ', 'R', 'C'].includes(event.key.toUpperCase())) {
            event.preventDefault();
        }

        this.activeKeys.add(event.key);

        // send
        const keysArray = Array.from(this.activeKeys).sort();
        this.sendUpdate({
            keys: keysArray
        });
    },

    handleKeyUp: function(event) {
        this.activeKeys.delete(event.key.toLowerCase());
    },

    handleMouseMove: function(event) {
        if (document.pointerLockElement) {
            const mouseData = {
                mouseMoveX: event.movementX,
                mouseMoveY: event.movementY,
            };
            this.sendUpdate(mouseData);
        }
    },

    sendUpdate: async function(data) {
        fetch('http://localhost:' + window.location.port + '/mnkevent', {
            method: 'POST',
            body: JSON.stringify(data),
            headers: { 'Content-Type': 'application/json' }
        });
    }
}
mnKEvent.init();
