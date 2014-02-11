console.log('Here goes nothing....');







var todoList = [
	{
   	description: 'Need bread',
   	done: false,
   	id: _.uniqueId()
  	},
  	{
   	description: 'kill bill',
   	done: true,
   	id: _.uniqueId()
  },
  {
   	description: 'fly a kite',
   	done: false,
   	id: _.uniqueId()
  	}
]


$(document).ready(function(){

  	var todoTemplate = _.template($('.todo-template').text())

  // render preloaded data
  	_.each(todoList, function(item){
  		$('.todo-items').prepend( todoTemplate(item) )
  	})

  	$('.todo-items').on('click', '.js-remove-todo', function(){
    	var parentId = $(this).parent().attr('id').split('-')[1];

    	todoList = _.reject(todoList, function(item){ 
      return item.id == parentId;
   	})

    	$(this).parent().remove();

  	})

  // Setup Add Button click event
  	$('.js-add-todo').click(function(){

    	var description = $('.js-new-todo-input').val();

    	var todo = {
      	description: description,
      	done: false,
      	id: _.uniqueId()
    	}

    	var renderedTemplate = todoTemplate(todo);

    	$('.todo-items').prepend(renderedTemplate);
  	

  	})

  	$('.todo-items').on('click', '.js-complete-todo', function(){
  		
  		$(this).siblings('.description').toggleClass('completed');

  		$(this).parent(_.each(todoList, function(item){
    			item.done = true;
 		 }))

  	})

});




