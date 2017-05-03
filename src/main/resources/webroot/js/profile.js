var AUTH = (function() {
  var userId;

  function init(user) {
    userId = user;
    /*var changePasswordCheckbox = $('#changePassword');
    if (changePasswordCheckbox.is(':checked')) {
      $('#passwordPanel').show();
    } else {
      $('#passwordPanel').hide();
    }
    changePasswordCheckbox.on('click', function() {
      if (changePasswordCheckbox.is(':checked')) {
        $('#passwordPanel').show();
      } else {
        $('#passwordPanel').hide();
      }
    });*/
    var newPasswordInput = $('#newPassword');
    var oldPasswordInput = $('#oldPassword');
    var newPasswordRepeatInput = $('#newPasswordRepeat');
    // Disbale spaces.
    oldPasswordInput.on('keydown', function(event) {if (event.which === 32) return false;});
    newPasswordInput.on('keydown', function(event) {if (event.which === 32) return false;});
    newPasswordRepeatInput.on('keydown', function(event) {if (event.which === 32) return false;});

    // Enable and disable submit button.
    var submitButton = $('#submit');
    newPasswordInput.on('propertychange change click keyup input paste', function(event) {
      var newPassword = $.trim(newPasswordInput.val());
      if (newPassword) {
        submitButton.prop('disabled', false);
      } else {
        submitButton.prop('disabled', true);
      }
    });
  }

  function submit() {
	  var fields = readFields();
    var isValid = validate(fields);
    var messagePanel = $('#message');
    messagePanel.hide();

    if (!isValid) return;

    var oldPasswordHash = CryptoJS.SHA256(fields.oldPassword).toString();
    var newPasswordHash = CryptoJS.SHA256(fields.newPassword).toString();

    var request = {
      userId : userId,
      oldPwd : oldPasswordHash,
      newPwd : newPasswordHash
    };
    console.log(request);

    $.ajax({
        url : 'changePassword',
        method : 'POST',
        contentType : 'application/json',
        data :  JSON.stringify(request),
        dataType : 'json',
        error : function(xhr, status, error) {
          if (xhr && xhr.responseJSON && xhr.responseJSON.code == 403) {
            var oldPasswordField = $('#oldPasswordField');
            oldPasswordField.find('div.hint').text("Das angegebene Passwort nicht korrekt.")
            oldPasswordField.addClass('error');
          } else {
            messagePanel.text("Ein unbekannter Fehler ist aufgetreten, bitte versuchen Sie es später erneut.");
            messagePanel.addClass('error');
            messagePanel.show();
          }
        },
        success : function(data, status, xhr) {
          messagePanel.text("Die Änderungen wurden übernommen. Bitte loggen Sie sich neu ein damit die Änderungen aktiv werden.");
          messagePanel.removeClass('error');
          messagePanel.show();
        }
    });
  }

  function validate(fields) {
    var isValid = true;
    var oldPasswordField = $('#oldPasswordField');
    var newPasswordField = $('#newPasswordField');
    var newPasswordRepeatField = $('#newPasswordRepeatField');

    if (!fields.oldPassword) {
      oldPasswordField.find('div.hint').text("Bitte geben Sie hier ihr altes Passwort ein.")
      oldPasswordField.addClass('error');
      isValid = false;
    } else {
      oldPasswordField.removeClass('error');
    }

    if (!(fields.newPasswordRepeat == fields.newPassword)) {
      newPasswordRepeatField.find('div.hint').text("Die eingegeben Passwörter sind nicht identisch.")
      newPasswordField.addClass('error');
      newPasswordRepeatField.addClass('error');
      isValid = false;
    } else if (fields.newPassword.length < 4) {
      newPasswordRepeatField.find('div.hint').text("Das neue Passwort muss mindestens 4 Zeichen enthalten.")
      newPasswordField.addClass('error');
      newPasswordRepeatField.addClass('error');
      isValid = false;
    } else {
      newPasswordField.removeClass('error');
      newPasswordRepeatField.removeClass('error');
    }
    return isValid;
  }

  function readFields() {
	  var fields = {};
	  fields.oldPassword = $.trim($('#oldPassword').val());
	  fields.newPassword = $.trim($('#newPassword').val());
	  fields.newPasswordRepeat = $.trim($('#newPasswordRepeat').val());
	  return fields;
  }

  return {
    init : init,
    submit : submit
  }
})();
