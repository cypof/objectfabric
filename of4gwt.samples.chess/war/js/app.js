$(document).ready(function() {
	$(".square img").draggable({
		start : function(event, ui) {
		},
		containment : '#legal',
		drag : function(event, ui) {
			var baseLeft = $('#legal').offset().left;
			var baseTop = $('#legal').offset().top;
			var _x = ui.offset.left - baseLeft;
			var _y = ui.offset.top - baseTop;

			alert($(ui.draggable).attr("src"));
			move(0, _x, _y);
		},
		stop : function(event, ui) {
		}
	});
});