console.log("Here goes nothing....");var todoList=[{description:"Need bread",done:!1,id:_.uniqueId()},{description:"kill bill",done:!0,id:_.uniqueId()},{description:"fly a kite",done:!1,id:_.uniqueId()}];$(document).ready(function(){var a=_.template($(".todo-template").text());_.each(todoList,function(b){$(".todo-items").prepend(a(b))}),$(".todo-items").on("click",".js-remove-todo",function(){var a=$(this).parent().attr("id").split("-")[1];todoList=_.reject(todoList,function(b){return b.id==a}),$(this).parent().remove()}),$(".js-add-todo").click(function(){var b=$(".js-new-todo-input").val(),c={description:b,done:!1,id:_.uniqueId()},d=a(c);$(".todo-items").prepend(d)}),$(".todo-items").on("click",".js-complete-todo",function(){$(this).siblings(".description").toggleClass("completed"),$(this).parent(_.each(todoList,function(a){a.done=!0}))})});