function Reader() {

  var singleTouchGesture = false;
  var startX = 0;
  var startY = 0;

  var handleTouchStart = function(event) {
    singleTouchGesture = event.touches.length == 1;

    var touch = event.changedTouches[0];

    startX = touch.screenX;
    startY = touch.screenY;
  };

  var handleTouchEnd = function(event) {
    if (!singleTouchGesture) return;

    var touch = event.changedTouches[0];

    var maxScreenX = screen.width;
    var relativeDistanceX = (touch.screenX - startX) / maxScreenX;

    // Tap to turn.
    if (Math.abs(relativeDistanceX) < 0.1) {
      var position = touch.screenX / maxScreenX;
      if (position <= 0.3) {
        window.location.href = "gesture:click-left";
      } else if(position >= 0.7) {
        window.location.href = "gesture:click-right";
      } else {
        window.location.href = "gesture:click-center";
      }
      event.stopPropagation();
      event.preventDefault();
      return;
    } else {
      var slope = (touch.screenY - startY) / (touch.screenX - startX);
      // Swipe to turn.
      if (Math.abs(slope) <= 0.5) {
        if(relativeDistanceX > 0) {
          window.location.href = "gesture:swipe-left";
        } else {
          window.location.href = "gesture:swipe-right";
        }
        event.stopPropagation();
        event.preventDefault();
        return;
      }
    }
  };

  // Handles gestures between inner content and edge of screen.
  document.addEventListener("touchstart", handleTouchStart, false);
  document.addEventListener("touchend", handleTouchEnd, false);

  // This should be called by the host whenever the page changes. This is because a change in the
  // page can mean a change in the iframe and thus requires resetting properties.
  this.pageDidChange = function() {
    var contentDocument = window.frames["epubContentIframe"].contentDocument;
    // Handles gestures for the inner content.
    contentDocument.removeEventListener("touchstart", handleTouchStart);
    contentDocument.addEventListener("touchstart", handleTouchStart, false);
    contentDocument.removeEventListener("touchend", handleTouchEnd);
    contentDocument.addEventListener("touchend", handleTouchEnd, false);
    // Set up page turning animation.
    contentDocument.documentElement.style["transition"] = "left 0.2s" ;
  };

}

simplified = new Reader();
