

// var Fake = [
//     'Hi there',
//     'Nice to meet you',
//     'How are you?',
//     'Not too bad, thanks',
//     'What do you do?',
//     'That\'s awesome',
//     'Codepen is a nice place to stay',
//     'I think you\'re a nice person',
//     'Why do you think that?',
//     'Can you explain?',
//     'Anyway I\'ve gotta go now',
//     'It was a pleasure chat with you',
//     'Time to make a new codepen',
//     'Bye',
//     ':)'
// ]

// function fakeMessage() {
//     if ($('.message-input').val() != '') {
//         return false;
//     }
//     $('<div class="message loading new"><figure class="avatar"><img src="img/favicon.png" /></figure><span></span></div>').appendTo($('.mCSB_container'));
//     updateScrollbar();
//
//     setTimeout(function() {
//         $('.message.loading').remove();
//         $('<div class="message new"><figure class="avatar"><img src="img/favicon.png" /></figure>' + Fake[i] + '</div>').appendTo($('.mCSB_container')).addClass('new');
//         setDate();
//         updateScrollbar();
//         i++;
//     }, 1000 + (Math.random() * 20) * 100);
//
// }